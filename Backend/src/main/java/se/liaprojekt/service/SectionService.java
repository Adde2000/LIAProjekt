package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Course;
import se.liaprojekt.model.Section;
import se.liaprojekt.repository.CourseRepository;
import se.liaprojekt.repository.SectionRepository;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;

    public Section addSection(Long courseId, String title) {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Course not found: " + courseId));

        int nextIndex =

                //  Kolla om kursen inte har några sections alls
                course.getSections().isEmpty()

                        //  Om inga sections finns → första section får index 0
                        ? 0

                        //  Annars: det finns redan sections i kursen
                        : course.getSections()

                        //  Ta sista section i listan (högst orderIndex pga @OrderBy("orderIndex ASC"))
                          .get(course.getSections().size() - 1)

                        //  Hämta dess orderIndex
                          .getOrderIndex()

                          //  +1 = nästa lediga index
                          + 1;

        Section section = new Section();
        section.setTitle(title);
        section.setOrderIndex(nextIndex);
        section.setCourse(course);

        return sectionRepository.save(section);
    }

}
