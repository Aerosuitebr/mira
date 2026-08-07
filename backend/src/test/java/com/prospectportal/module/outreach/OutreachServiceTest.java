package com.prospectportal.module.outreach;

import com.prospectportal.module.outreach.entity.OutreachMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutreachServiceTest {

    @Test
    void automaticSkippedMessageIsRetryable() {
        OutreachMessage message = message("AUTO", "SKIPPED");

        assertThat(OutreachService.isRetryable(message)).isTrue();
    }

    @Test
    void automaticFailedAndThrottledMessagesAreRetryable() {
        assertThat(OutreachService.isRetryable(message("AUTO", "FAILED"))).isTrue();
        assertThat(OutreachService.isRetryable(message("AUTO", "THROTTLED"))).isTrue();
    }

    @Test
    void sentAndEmailMessagesRemainIneligible() {
        assertThat(OutreachService.isRetryable(message("AUTO", "SENT"))).isFalse();
        assertThat(OutreachService.isRetryable(message("EMAIL", "SKIPPED"))).isFalse();
    }

    private static OutreachMessage message(String channel, String status) {
        OutreachMessage message = new OutreachMessage();
        message.setChannel(channel);
        message.setStatus(status);
        return message;
    }
}
