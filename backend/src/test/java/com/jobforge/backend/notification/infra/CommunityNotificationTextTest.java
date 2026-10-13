package com.jobforge.backend.notification.infra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.notification.app.NotificationService;
import com.jobforge.backend.platform.events.EventEnvelope;
import com.jobforge.backend.platform.events.IdempotentEventConsumer;
import com.jobforge.backend.user.facade.UserSummaryFacade;
import com.jobforge.backend.user.facade.UserSummaryFacade.UserSummaryView;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Community notifications name the actor and never notify about one's own action. NOT RUN in the authoring session. */
class CommunityNotificationTextTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final IdempotentEventConsumer idempotent = mock(IdempotentEventConsumer.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserSummaryFacade users = mock(UserSummaryFacade.class);
    private final NotificationKafkaConsumer consumer = new NotificationKafkaConsumer(idempotent, notifications, mapper, users);
    private final UUID actor = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID post = UUID.randomUUID();

    @BeforeEach
    void wire() {
        doAnswer(inv -> {
            Consumer<EventEnvelope> handler = inv.getArgument(2);
            handler.accept(mapper.readValue((String) inv.getArgument(1), EventEnvelope.class));
            return null;
        }).when(idempotent).consume(any(), any(), any());
        when(users.summary(actor)).thenReturn(Optional.of(new UserSummaryView(actor, "asha_k", "Asha", "Kulkarni", null, "JOB_SEEKER", true)));
    }

    private String event(String type, Map<String, Object> payload) throws Exception {
        return mapper.writeValueAsString(Map.of("eventId", UUID.randomUUID().toString(), "eventType", type, "eventVersion", 1,
                "occurredAt", "2026-10-10T10:00:00Z", "producer", "test", "aggregateType", "Post",
                "aggregateId", post.toString(), "payload", payload));
    }

    @Test
    void likeNotificationNamesTheLiker() throws Exception {
        consumer.consume(event("PostLiked", Map.of("postId", post.toString(), "likerId", actor.toString(), "postAuthorId", owner.toString())));
        verify(notifications).create(eq(owner), eq("POST_LIKED"), any(), eq("Asha Kulkarni liked your post."), any(), any());
    }

    @Test
    void unknownActorFallsBackToSomeone() throws Exception {
        UUID stranger = UUID.randomUUID();
        when(users.summary(stranger)).thenReturn(Optional.empty());
        consumer.consume(event("UserFollowed", Map.of("followerId", stranger.toString(), "followeeId", owner.toString())));
        verify(notifications).create(eq(owner), eq("USER_FOLLOWED"), any(), eq("Someone started following you."), any(), any());
    }

    @Test
    void replyAndCommentNotifyTheRightPeopleOnceAndNeverTheAuthor() throws Exception {
        UUID parentAuthor = UUID.randomUUID();
        UUID comment = UUID.randomUUID();
        consumer.consume(event("CommentCreated", Map.of("commentId", comment.toString(), "postId", post.toString(),
                "authorId", actor.toString(), "parentCommentId", UUID.randomUUID().toString(),
                "postAuthorId", owner.toString(), "parentAuthorId", parentAuthor.toString())));
        verify(notifications).create(eq(parentAuthor), eq("COMMENT_REPLIED"), any(), eq("Asha Kulkarni replied to your comment."), any(), any());
        verify(notifications).create(eq(owner), eq("POST_COMMENTED"), any(), eq("Asha Kulkarni commented on your post."), any(), any());
    }

    @Test
    void selfActionsProduceNoNotification() throws Exception {
        consumer.consume(event("CommentCreated", Map.of("commentId", UUID.randomUUID().toString(), "postId", post.toString(),
                "authorId", actor.toString(), "postAuthorId", actor.toString())));
        verify(notifications, never()).create(any(), any(), any(), any(), any(), any());
    }
}
