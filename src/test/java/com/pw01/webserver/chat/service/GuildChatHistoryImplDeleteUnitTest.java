package com.pw01.webserver.chat.service;

import com.pw01.webserver.chat.repository.GuildChatMessageRepository;
import com.pw01.webserver.config.ChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GuildChatHistoryImpl 지우기 두 메서드의 묶음 반복을 DB 없이 본다: 묶음마다 새 트랜잭션(REQUIRES_NEW), 묶음보다 적게 지워지면 끝.
 * 실패하면 deleteInBatches의 끝 조건(count < batchSize)과 TransactionTemplate 전파 설정을 의심한다.
 */
class GuildChatHistoryImplDeleteUnitTest {

    private static final int BATCH = 1000;

    private final GuildChatMessageRepository repository = mock(GuildChatMessageRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final GuildChatHistoryImpl history = new GuildChatHistoryImpl(repository, transactionManager, properties());

    // 확인: 2500줄 → 1000·1000·500 세 묶음, 묶음마다 REQUIRES_NEW 트랜잭션을 열고 커밋함
    @Test
    void 묶음으로_나눠_지움() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.deleteBatchCreatedBefore(any(Instant.class), eq(BATCH))).thenReturn(1000, 1000, 500);

        assertThat(history.deleteExpired()).isEqualTo(2500);

        verify(repository, times(3)).deleteBatchCreatedBefore(any(Instant.class), eq(BATCH));
        verify(transactionManager, times(3)).getTransaction(argThat(
                definition -> definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactionManager, times(3)).commit(any());
    }

    // 확인: 꼭 묶음 크기만큼 남았으면 한 번 더 물어 0줄을 보고 끝
    @Test
    void 딱_맞게_남았으면_한_번_더() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.deleteBatchOfGuild(7L, BATCH)).thenReturn(1000, 0);

        assertThat(history.deleteGuild("7")).isEqualTo(1000);

        verify(repository, times(2)).deleteBatchOfGuild(7L, BATCH);
    }

    // 확인: 숫자가 아닌 길드 ID는 DB에 묻지 않고 0
    @Test
    void 숫자가_아닌_길드는_넘어감() {
        assertThat(history.deleteGuild("abc")).isZero();
        assertThat(history.deleteGuild("١٢")).isZero();

        verify(repository, never()).deleteBatchOfGuild(anyLong(), anyInt());
    }

    private static ChatProperties properties() {
        return new ChatProperties(100,
                new ChatProperties.Rate(Duration.ofSeconds(10), 5),
                new ChatProperties.Mute(3, Duration.ofSeconds(60), Duration.ofSeconds(60)),
                new ChatProperties.Guild(Duration.ofDays(3), 10, 50),
                new ChatProperties.Cleanup(true, "0 30 3 * * *", BATCH),
                new ChatProperties.Recent(true, 10));
    }

}
