package se.liaprojekt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import se.liaprojekt.dto.SectionResponse;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.Section;
import se.liaprojekt.model.TestResult;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.SectionRepository;
import se.liaprojekt.repository.TestResultRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectionServiceTest {

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private BlobStorageService blobStorageService;

    @InjectMocks
    private SectionService sectionService;

    private Course course;
    private Section section;

    @BeforeEach
    void setUp() {

        course = new Course();
        course.setId(1L);

        section = new Section();
        section.setId(10L);
        section.setTitle("Intro");
        section.setOrderIndex(0);
        section.setCourse(course);
    }

    @Test
    void shouldAddFirstSection() {

        // Arrange
        course.setSections(List.of());

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        when(sectionRepository.save(any(Section.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SectionResponse response =
                sectionService.addSection(1L, "New Section");

        // Assert
        assertEquals("New Section", response.title());
        assertEquals(0, response.orderIndex());
        assertEquals(1L, response.courseId());
    }

    @Test
    void shouldAddSectionWithNextIndex() {

        // Arrange
        Section existing = new Section();
        existing.setOrderIndex(2);

        course.setSections(List.of(existing));

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        when(sectionRepository.save(any(Section.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SectionResponse response =
                sectionService.addSection(1L, "Advanced");

        // Assert
        assertEquals(3, response.orderIndex());
    }

    @Test
    void shouldThrowWhenCourseNotFoundOnAddSection() {

        when(courseRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> sectionService.addSection(1L, "Test")
        );
    }

    @Test
    void shouldGetSections() {

        // Arrange
        course.setSections(List.of(section));

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));

        // Act
        List<SectionResponse> result =
                sectionService.getSections(1L, "entra-123");

        // Assert
        assertEquals(1, result.size());

        SectionResponse response = result.get(0);

        assertEquals("Intro", response.title());
        assertFalse(response.isLocked());
    }

    @Test
    void shouldThrowWhenCourseNotFoundOnGetSections() {

        when(courseRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> sectionService.getSections(1L, "entra")
        );
    }

    @Test
    void shouldDeleteSection() {

        // Act
        sectionService.deleteSection(10L);

        // Assert
        verify(blobStorageService)
                .deleteSectionFiles(10L);

        verify(sectionRepository)
                .deleteById(10L);
    }

    @Test
    void shouldReturnFalseWhenFirstSectionIsChecked() {

        section.setOrderIndex(0);

        boolean locked =
                sectionService.isSectionLocked(
                        section,
                        "entra-123"
                );

        assertFalse(locked);
    }

    @Test
    void shouldReturnTrueWhenPreviousSectionNotCompleted() {

        // Arrange
        Section previous = new Section();
        previous.setId(5L);

        section.setOrderIndex(1);

        when(sectionRepository.findByCourseIdAndOrderIndex(1L, 0))
                .thenReturn(Optional.of(previous));

        when(testResultRepository
                .findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        5L
                ))
                .thenReturn(List.of());

        // Act
        boolean locked =
                sectionService.isSectionLocked(
                        section,
                        "entra-123"
                );

        // Assert
        assertTrue(locked);
    }

    @Test
    void shouldReturnFalseWhenPreviousSectionCompleted() {

        // Arrange
        Section previous = new Section();
        previous.setId(5L);

        section.setOrderIndex(1);

        TestResult result = new TestResult();
        result.setStatus(TestResult.Status.COMPLETED);

        when(sectionRepository.findByCourseIdAndOrderIndex(1L, 0))
                .thenReturn(Optional.of(previous));

        when(testResultRepository
                .findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        5L
                ))
                .thenReturn(List.of(result));

        // Act
        boolean locked =
                sectionService.isSectionLocked(
                        section,
                        "entra-123"
                );

        // Assert
        assertFalse(locked);
    }

    @Test
    void shouldReturnTrueWhenPreviousSectionFailed() {

        // Arrange
        Section previous = new Section();
        previous.setId(5L);

        section.setOrderIndex(1);

        TestResult result = new TestResult();
        result.setStatus(TestResult.Status.FAILED);

        when(sectionRepository.findByCourseIdAndOrderIndex(1L, 0))
                .thenReturn(Optional.of(previous));

        when(testResultRepository
                .findByUser_EntraIdAndSectionIdOrderByAttemptNumberDesc(
                        "entra-123",
                        5L
                ))
                .thenReturn(List.of(result));

        // Act
        boolean locked =
                sectionService.isSectionLocked(
                        section,
                        "entra-123"
                );

        // Assert
        assertTrue(locked);
    }

    @Test
    void shouldThrowWhenPreviousSectionMissing() {

        // Arrange
        section.setOrderIndex(1);

        when(sectionRepository.findByCourseIdAndOrderIndex(1L, 0))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> sectionService.isSectionLocked(
                        section,
                        "entra-123"
                )
        );
    }
}