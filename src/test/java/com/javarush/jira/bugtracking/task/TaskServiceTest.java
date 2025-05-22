package com.javarush.jira.bugtracking.task;

import com.javarush.jira.bugtracking.Handlers;
import com.javarush.jira.bugtracking.UserBelongRepository;
import com.javarush.jira.bugtracking.sprint.SprintRepository;
import com.javarush.jira.bugtracking.task.mapper.TaskExtMapper;
import com.javarush.jira.bugtracking.task.mapper.TaskFullMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private Handlers.TaskExtHandler taskExtHandler;
    @Mock
    private Handlers.ActivityHandler activityHandler;
    @Mock
    private TaskFullMapper taskFullMapper;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private TaskExtMapper taskExtMapper;
    @Mock
    private UserBelongRepository userBelongRepository;
    @Mock
    private Handlers.TaskFullHandler taskFullHandler;
    @Mock
    private TaskFullMapper taskFullMapper2;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private TaskService taskService;

    private Task task;
    private final LocalDateTime T1 = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
    private final LocalDateTime T2 = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
    private final LocalDateTime T3 = LocalDateTime.of(2024, 1, 1, 14, 0, 0);

    @BeforeEach
    void setUp() {
        task = new Task();
        task.setId(1L);
        when(activityHandler.getRepository()).thenReturn(activityRepository);
    }

    private Activity createActivity(String statusCode, LocalDateTime updated) {
        Activity activity = new Activity();
        activity.setStatusCode(statusCode);
        activity.setUpdated(updated);
        activity.setId(System.nanoTime());
        activity.setTaskId(task.getId());
        activity.setAuthorId(1L);
        return activity;
    }


    @Test
    void testCalculateDevelopmentTime_success() {
        List<Activity> activities = List.of(
                createActivity("ready_for_review", T2),
                createActivity("in_progress", T1)
        );
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateDevelopmentTime(task);
        assertEquals(Duration.between(T1, T2), result);
    }

    @Test
    void testCalculateDevelopmentTime_multipleRelevantEvents() {
        Activity olderInProgress = createActivity("in_progress", T1.minusHours(2));
        Activity latestInProgress = createActivity("in_progress", T1);
        Activity olderReadyForReview = createActivity("ready_for_review", T2.minusHours(1));
        Activity latestReadyForReview = createActivity("ready_for_review", T2);

        List<Activity> activities = List.of(
                latestReadyForReview,
                olderReadyForReview,
                latestInProgress,
                olderInProgress
        );
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateDevelopmentTime(task);
        assertEquals(Duration.between(T1, T2), result);
    }


    @Test
    void testCalculateDevelopmentTime_missingInProgress() {
        List<Activity> activities = List.of(createActivity("ready_for_review", T2));
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateDevelopmentTime(task);
        assertEquals(Duration.ZERO, result);
    }

    @Test
    void testCalculateDevelopmentTime_missingReadyForReview() {
        List<Activity> activities = List.of(createActivity("in_progress", T1));
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateDevelopmentTime(task);
        assertEquals(Duration.ZERO, result);
    }

    @Test
    void testCalculateDevelopmentTime_timestampsInverted() {
        List<Activity> activities = List.of(
                createActivity("in_progress", T2),
                createActivity("ready_for_review", T1)
        );
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateDevelopmentTime(task);
        assertEquals(Duration.ZERO, result);
    }

    @Test
    void testCalculateDevelopmentTime_emptyActivities() {
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(Collections.emptyList());

        Duration result = taskService.calculateDevelopmentTime(task);
        assertEquals(Duration.ZERO, result);
    }


    @Test
    void testCalculateTestingTime_success() {
        List<Activity> activities = List.of(
                createActivity("done", T3),
                createActivity("ready_for_review", T2)
        );
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateTestingTime(task);
        assertEquals(Duration.between(T2, T3), result);
    }

    @Test
    void testCalculateTestingTime_multipleRelevantEvents() {
        Activity olderReadyForReview = createActivity("ready_for_review", T2.minusHours(1));
        Activity latestReadyForReview = createActivity("ready_for_review", T2);
        Activity olderDone = createActivity("done", T3.minusHours(1));
        Activity latestDone = createActivity("done", T3);

        List<Activity> activities = List.of(
                latestDone,
                olderDone,
                latestReadyForReview,
                olderReadyForReview
        );
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateTestingTime(task);
        assertEquals(Duration.between(T2, T3), result); // Expected: T3 - T2
    }

    @Test
    void testCalculateTestingTime_missingReadyForReview() {
        List<Activity> activities = List.of(createActivity("done", T3));
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateTestingTime(task);
        assertEquals(Duration.ZERO, result);
    }

    @Test
    void testCalculateTestingTime_missingDone() {
        List<Activity> activities = List.of(createActivity("ready_for_review", T2));
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateTestingTime(task);
        assertEquals(Duration.ZERO, result);
    }

    @Test
    void testCalculateTestingTime_timestampsInverted() {
        List<Activity> activities = List.of(
                createActivity("ready_for_review", T3),
                createActivity("done", T2)
        );
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(activities);

        Duration result = taskService.calculateTestingTime(task);
        assertEquals(Duration.ZERO, result);
    }

    @Test
    void testCalculateTestingTime_emptyActivities() {
        when(activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId())).thenReturn(Collections.emptyList());

        Duration result = taskService.calculateTestingTime(task);
        assertEquals(Duration.ZERO, result);
    }

}