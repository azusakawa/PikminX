package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class OcrScannerTest {
    @Test
    public void declaresEveryBundledOnDeviceScript() {
        assertEquals(List.of("Latin", "Chinese", "Devanagari", "Japanese", "Korean"),
                OcrScanner.supportedScriptNames());
    }

    @Test
    public void fullChineseSelectsOnlyChineseRecognizer() {
        assertEquals(
                List.of("Chinese"),
                OcrScanner.selectedScriptNames(OcrScan.Profile.FULL_CHINESE));
        assertEquals(
                OcrScanner.supportedScriptNames(),
                OcrScanner.selectedScriptNames(OcrScan.Profile.FULL_MULTILINGUAL));
    }

    @Test
    public void sameGenerationAndCaptureStillCreateUniqueRequests() {
        OcrScanner.TransactionRegistry registry = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction first = registry.begin(4L, 9L);
        assertTrue(first.tryFinish(OcrScanner.TerminalState.SUCCESS));
        assertTrue(registry.clear(first));
        OcrScanner.Transaction second = registry.begin(4L, 9L);

        assertNotEquals(first.id(), second.id());
        assertTrue(second.id().ocrRequestSequence() > first.id().ocrRequestSequence());
    }

    @Test
    public void timeoutWinsOnceAndLateCallbacksAreIgnored() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(1L, 1L, 1L));
        AtomicInteger failureDeliveries = new AtomicInteger();

        if (transaction.tryFinish(OcrScanner.TerminalState.TIMEOUT)) {
            failureDeliveries.incrementAndGet();
        }
        if (transaction.tryFinish(OcrScanner.TerminalState.FAILURE)) {
            failureDeliveries.incrementAndGet();
        }

        assertEquals(1, failureDeliveries.get());
        assertFalse(transaction.tryFinish(OcrScanner.TerminalState.SUCCESS));
        assertEquals(OcrScanner.TerminalState.TIMEOUT, transaction.state());
    }

    @Test
    public void successFirstRejectsLaterTimeout() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(1L, 1L, 1L));

        assertTrue(transaction.tryFinish(OcrScanner.TerminalState.SUCCESS));
        assertFalse(transaction.tryFinish(OcrScanner.TerminalState.TIMEOUT));
        assertEquals(OcrScanner.TerminalState.SUCCESS, transaction.state());
    }

    @Test
    public void multilingualFailureBlocksPartialFrame() {
        assertFalse(OcrScanner.canDeliverCompleteFrame(
                5, 5, new IllegalStateException("one recognizer failed")));
        assertFalse(OcrScanner.canDeliverCompleteFrame(5, 4, null));
        assertTrue(OcrScanner.canDeliverCompleteFrame(5, 5, null));
    }

    @Test
    public void oldTransactionCannotClearNewActiveTransaction() {
        OcrScanner.TransactionRegistry registry = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction old = registry.begin(1L, 1L);
        assertTrue(old.tryFinish(OcrScanner.TerminalState.TIMEOUT));
        assertTrue(registry.clear(old));
        OcrScanner.Transaction current = registry.begin(1L, 2L);

        assertFalse(registry.clear(old));
        assertSame(current, registry.active());
        assertTrue(registry.isActive(current));
    }

    @Test
    public void cancelledTransactionRejectsLateSuccess() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(1L, 1L, 1L));

        assertTrue(transaction.tryFinish(OcrScanner.TerminalState.CANCELLED));
        assertFalse(transaction.tryFinish(OcrScanner.TerminalState.SUCCESS));
    }

    @Test
    public void terminalTransactionIsRejectedBeforeCallbackParsing() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(1L, 1L, 1L));
        AtomicInteger parses = new AtomicInteger();

        if (OcrScanner.canProcessCallback(transaction, () -> true)) {
            parses.incrementAndGet();
        }
        assertTrue(transaction.tryFinish(OcrScanner.TerminalState.CANCELLED));
        if (OcrScanner.canProcessCallback(transaction, () -> true)) {
            parses.incrementAndGet();
        }

        assertEquals(1, parses.get());
    }

    @Test
    public void ownerAdmissionRejectsCallbackWhenAutomationIsNoLongerValid() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(1L, 1L, 1L));

        assertFalse(OcrScanner.canProcessCallback(transaction, () -> false));
    }

    @Test
    public void registryRejectsCallbackAfterNewerGenerationReplacesIt() {
        OcrScanner.TransactionRegistry registry = new OcrScanner.TransactionRegistry();
        OcrScanner.Transaction old = registry.begin(1L, 1L);
        assertTrue(registry.clear(old));
        OcrScanner.Transaction current = registry.begin(2L, 2L);

        assertFalse(registry.acceptsCallback(old));
        assertTrue(registry.acceptsCallback(current));
    }

    @Test
    public void cancelledTransactionReleasesScannerResourceAfterLateTasksFinish() {
        OcrScanner.Transaction transaction = new OcrScanner.Transaction(
                new OcrScan.TransactionId(1L, 1L, 1L));
        AtomicInteger releases = new AtomicInteger();
        OcrScanner.TaskResourceLease resources =
                new OcrScanner.TaskResourceLease(2, releases::incrementAndGet);
        transaction.onTerminal(() -> {
            if (transaction.state() != OcrScanner.TerminalState.SUCCESS) {
                resources.releaseAfterTasks();
            }
        });

        assertTrue(transaction.tryFinish(OcrScanner.TerminalState.CANCELLED));
        assertFalse(resources.released());
        assertFalse(resources.taskComplete());
        assertTrue(resources.taskComplete());
        assertEquals(1, releases.get());
    }

    @Test
    public void scannerOwnedResourceWaitsForAllTasksBeforeRelease() {
        AtomicInteger releases = new AtomicInteger();
        OcrScanner.TaskResourceLease resources =
                new OcrScanner.TaskResourceLease(3, releases::incrementAndGet);

        resources.releaseAfterTasks();
        assertFalse(resources.released());
        assertFalse(resources.taskComplete());
        assertFalse(resources.taskComplete());
        assertTrue(resources.taskComplete());
        resources.releaseAfterTasks();

        assertTrue(resources.released());
        assertEquals(1, releases.get());
    }

    @Test
    public void successfulFrameResourceStaysAliveUntilDeliveryReleasesIt() {
        AtomicInteger releases = new AtomicInteger();
        OcrScanner.TaskResourceLease resources =
                new OcrScanner.TaskResourceLease(1, releases::incrementAndGet);

        assertTrue(resources.taskComplete());
        assertFalse(resources.released());
        assertEquals(0, releases.get());

        // The success callback is the consumer of Frame.pixelAtSource().
        resources.releaseAfterTasks();
        assertTrue(resources.released());
        resources.releaseAfterTasks();
        assertEquals(1, releases.get());
    }
}
