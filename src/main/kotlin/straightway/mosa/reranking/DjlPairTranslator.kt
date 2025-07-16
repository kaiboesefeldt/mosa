package straightway.mosa.reranking

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.ndarray.NDList
import ai.djl.translate.Batchifier
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import java.nio.file.Path
import kotlin.math.exp
import kotlin.math.min

class DjlPairTranslator(
    private val modelPath: Path,
) : Translator<String, Float> {
    private lateinit var tokenizer: HuggingFaceTokenizer

    override fun prepare(ctx: TranslatorContext) {
        tokenizer =
            HuggingFaceTokenizer
                .builder()
                .optTokenizerPath(modelPath.resolve("tokenizer.json"))
                .optPadding(true)
                .optTruncation(true)
                .optMaxLength(SEQ_MAXLEN)
                .build()
    }

    override fun processInput(
        ctx: TranslatorContext,
        input: String,
    ): NDList {
        val manager = ctx.ndManager
        val encoding = tokenizer.encode(input)

        // --- explicit pad / truncate to global SEQ_MAXLEN (shape consistency) ---
        val idsPadded = LongArray(SEQ_MAXLEN)
        val maskPadded = LongArray(SEQ_MAXLEN)
        val copyLen = min(encoding.ids.size, SEQ_MAXLEN)
        System.arraycopy(encoding.ids, 0, idsPadded, 0, copyLen)
        System.arraycopy(encoding.attentionMask, 0, maskPadded, 0, copyLen)

        // NOTE: **no expandDims here** – keep rank‑1; Batchifier.STACK → rank‑2
        val idsNd = manager.create(idsPadded) // (SEQ_MAXLEN)
        val maskNd = manager.create(maskPadded) // (SEQ_MAXLEN)
        return NDList(idsNd, maskNd)
    }

    override fun processOutput(
        ctx: TranslatorContext,
        list: NDList,
    ): Float {
        val logits = list.singletonOrThrow() // (1, 1)
        val logitVal = logits.getFloat(0)
        // Sigmoid manually: 1 / (1 + exp(-x))
        return 1f / (1f + exp(-logitVal))
    }

    override fun getBatchifier(): Batchifier = Batchifier.STACK // batching safe; returns rank‑2

    private companion object {
        private const val SEQ_MAXLEN = 512 // must match model config (512 for bge‑reranker‑base)
    }
}
