#include <jni.h>
#include <llama.h>
#include <mtmd.h>
#include <mtmd-helper.h>
#include <atomic>
#include <algorithm>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>
#include <memory>
#include <stdexcept>

namespace {
std::mutex lock;
std::atomic<bool> cancelled{false};
std::atomic<int64_t> deadline{0};
llama_model *model = nullptr;
llama_context *ctx = nullptr;
mtmd_context *vision = nullptr;
int64_t now() { return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
bool abort_inference(void *) { return cancelled.load() || (deadline.load() != 0 && now() > deadline.load()); }
void quiet_log(ggml_log_level, const char *, void *) {} // No prompt / screenshot / generated text in logcat.
void clear() { if (vision) mtmd_free(vision); if (ctx) llama_free(ctx); if (model) llama_model_free(model); vision=nullptr; ctx=nullptr; model=nullptr; }
std::string bytes(JNIEnv *env, jbyteArray input) {
    if (!input) return {};
    std::string value(env->GetArrayLength(input), '\0');
    env->GetByteArrayRegion(input, 0, value.size(), reinterpret_cast<jbyte *>(value.data()));
    return value;
}
void fail(JNIEnv *env, const char *code = "LOCAL_BACKEND_FAILED") { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), code); }
void require(bool condition, const char *code = "LOCAL_BACKEND_FAILED") { if (!condition) throw std::runtime_error(code); }
}
extern "C" JNIEXPORT void JNICALL
Java_org_phoneai_teamwork_model_LocalUiModel_nativeLoad(JNIEnv *env, jobject, jbyteArray path, jbyteArray projector, jint threads) {
    std::lock_guard<std::mutex> guard(lock);
    try {
        clear(); cancelled=false; deadline=now()+120000;
        llama_log_set(quiet_log, nullptr); mtmd_helper_log_set(quiet_log, nullptr);
        llama_backend_init();
        auto mp=llama_model_default_params(); mp.n_gpu_layers=0;
        mp.progress_callback=[](float, void *) { return !abort_inference(nullptr); };
        const auto model_path=bytes(env,path); const auto proj_path=bytes(env,projector);
        model=llama_model_load_from_file(model_path.c_str(),mp); require(model,"MODEL_LOAD_FAILED");
        auto cp=llama_context_default_params(); cp.n_ctx=4096; cp.n_batch=256; cp.n_ubatch=256;
        cp.n_threads=threads; cp.n_threads_batch=threads; cp.abort_callback=abort_inference;
        ctx=llama_init_from_model(model,cp); require(ctx,"CONTEXT_CREATE_FAILED");
        if (!proj_path.empty()) {
            auto vp=mtmd_context_params_default(); vp.use_gpu=false; vp.n_threads=threads; vp.warmup=false;
            vp.image_max_tokens=1024; vp.progress_callback=[](float,void *) { return !abort_inference(nullptr); };
            vision=mtmd_init_from_file(proj_path.c_str(),model,vp); require(vision && mtmd_support_vision(vision),"PROJECTOR_LOAD_FAILED");
        }
        deadline=0;
    } catch (const std::exception &e) { clear(); deadline=0; fail(env,e.what()); }
    catch (...) { clear(); deadline=0; fail(env); }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_phoneai_teamwork_model_LocalUiModel_nativeGenerate(JNIEnv *env,jobject,jbyteArray input,jbyteArray rgb,jint width,jint height) {
    std::lock_guard<std::mutex> guard(lock);
    try {
        require(model && ctx && !cancelled.load()); deadline=now()+90000;
        llama_memory_clear(llama_get_memory(ctx),true);
        std::string content=bytes(env,input);
        if (rgb) { require(vision && width>0 && height>0 && width<=1024 && height<=1024); content=std::string(mtmd_default_marker())+"\n"+content; }
        llama_chat_message message{"user",content.c_str()};
        const char *tmpl=llama_model_chat_template(model,nullptr); require(tmpl,"CHAT_TEMPLATE_MISSING");
        int length=llama_chat_apply_template(tmpl,&message,1,true,nullptr,0); require(length>0 && length<65536,"CHAT_TEMPLATE_UNSUPPORTED");
        std::vector<char> rendered(length+1);
        int actual=llama_chat_apply_template(tmpl,&message,1,true,rendered.data(),rendered.size()); require(actual==length);
        std::string prompt(rendered.data(),length);
        if (vision) {
            std::unique_ptr<mtmd_input_chunks,decltype(&mtmd_input_chunks_free)> chunks(mtmd_input_chunks_init(),mtmd_input_chunks_free);
            std::unique_ptr<mtmd_bitmap,decltype(&mtmd_bitmap_free)> bitmap(nullptr,mtmd_bitmap_free);
            const mtmd_bitmap *list[1]={nullptr};
            if (rgb) { auto data=bytes(env,rgb); require(data.size()==static_cast<size_t>(width*height*3)); bitmap.reset(mtmd_bitmap_init(width,height,reinterpret_cast<const unsigned char *>(data.data()))); require(bitmap.get()); list[0]=bitmap.get(); }
            mtmd_input_text text{prompt.c_str(),prompt.size(),true,true};
            require(mtmd_tokenize(vision,chunks.get(),&text,list,rgb?1:0)==0,"MTMD_TOKENIZE_FAILED");
            require(mtmd_helper_get_n_tokens(chunks.get())+256 < llama_n_ctx(ctx),"CONTEXT_BUDGET_EXCEEDED");
            llama_pos position=0;
            const int prefill_result = mtmd_helper_eval_chunks(vision,ctx,chunks.get(),0,0,256,true,&position);
            require(!abort_inference(nullptr),"CANCELLED_OR_TIMEOUT");
            require(prefill_result==0,"MTMD_PREFILL_FAILED");
        } else {
            const auto *vocab=llama_model_get_vocab(model);
            int count=llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true); require(count<0);
            std::vector<llama_token> tokens(-count);
            require(llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),tokens.size(),true,true)==static_cast<int>(tokens.size()));
            require(tokens.size()+256<llama_n_ctx(ctx));
            for (size_t i=0;i<tokens.size();i+=256) {
                require(!abort_inference(nullptr),"CANCELLED_OR_TIMEOUT"); auto batch=llama_batch_get_one(tokens.data()+i,std::min<size_t>(256,tokens.size()-i));
                require(llama_decode(ctx,batch)==0);
            }
        }
        std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(),llama_sampler_free);
        std::string output; const auto *vocab=llama_model_get_vocab(model); bool ended=false;
        for (int i=0;i<256;i++) {
            require(!abort_inference(nullptr),"CANCELLED_OR_TIMEOUT"); auto token=llama_sampler_sample(sampler.get(),ctx,-1);
            if (llama_vocab_is_eog(vocab,token)) { ended=true; break; }
            std::vector<char> piece(256); int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,true);
            if (n<0) { piece.resize(-n); n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,true); }
            require(n>=0); output.append(piece.data(),n); require(output.size()<=4096);
            auto batch=llama_batch_get_one(&token,1); require(llama_decode(ctx,batch)==0);
        }
        require(ended,"OUTPUT_TOKEN_BUDGET_EXCEEDED"); deadline=0;
        jbyteArray result=env->NewByteArray(output.size());
        if (result) env->SetByteArrayRegion(result,0,output.size(),reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception &e) { deadline=0; fail(env,e.what()); return nullptr; }
    catch (...) { deadline=0; fail(env); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_org_phoneai_teamwork_model_LocalUiModel_nativeCancel(JNIEnv *,jobject) { cancelled=true; }
extern "C" JNIEXPORT void JNICALL
Java_org_phoneai_teamwork_model_LocalUiModel_nativeClose(JNIEnv *,jobject) { std::lock_guard<std::mutex> guard(lock); clear(); }

extern "C" JNIEXPORT void JNICALL
Java_org_phoneai_teamwork_model_LocalUiModel_nativePrepare(JNIEnv *,jobject) { cancelled=false; }
