package github.jhkoder.aiblog.infra.ai;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트 전용 MockAiClient.
 * dump_post.txt 내용을 청크로 분할해 chunkDelayMs 간격으로 emit.
 *
 * local  프로파일: chunkDelayMs=100  → ~10초
 * 단위 테스트 직접 생성: chunkDelayMs=0 → 즉시
 */
public class MockAiClient implements AiClient {

    private final long chunkDelayMs;
    private final String content;

    public MockAiClient(String content, long chunkDelayMs) {
        this.content = content;
        this.chunkDelayMs = chunkDelayMs;
    }

    /** 단위 테스트용 — 지연 없음 */
    public MockAiClient(String content) {
        this(content, 0L);
    }

    @Override
    public String complete(String prompt, String model, String apiKey) {
        return content;
    }

    @Override
    public Flux<String> streamComplete(String prompt, String model, String apiKey) {
        List<String> chunks = splitIntoChunks(content, 50);
        Flux<String> flux = Flux.fromIterable(chunks);
        if (chunkDelayMs > 0) {
            return flux.delayElements(Duration.ofMillis(chunkDelayMs));
        }
        return flux;
    }

    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, len)));
        }
        return chunks;
    }
}
