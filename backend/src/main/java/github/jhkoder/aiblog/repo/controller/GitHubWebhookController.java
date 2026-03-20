package github.jhkoder.aiblog.repo.controller;

import github.jhkoder.aiblog.infra.github.WebhookSignatureVerifier;
import github.jhkoder.aiblog.repo.usecase.HandleWebhookEventUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhook/github")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final HandleWebhookEventUseCase handleWebhookEventUseCase;
    private final WebhookSignatureVerifier signatureVerifier;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", defaultValue = "") String signature,
            @RequestBody String payload) {

        if (!signatureVerifier.verify(payload, signature)) {
            log.warn("Webhook 서명 검증 실패 — 403 반환");
            return ResponseEntity.status(403).build();
        }

        handleWebhookEventUseCase.execute(event, payload);
        return ResponseEntity.ok().build();
    }
}
