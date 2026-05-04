package se.liaprojekt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.liaprojekt.dto.MaterialRequest;
import se.liaprojekt.dto.MaterialResponse;
import se.liaprojekt.exception.ResourceNotFoundException;
import se.liaprojekt.model.Material;
import se.liaprojekt.model.Section;
import se.liaprojekt.repository.MaterialRepository;
import se.liaprojekt.repository.SectionRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final SectionRepository sectionRepository;

    public List<MaterialResponse> getBySection(Long sectionId) {
        return materialRepository.findBySectionId(sectionId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public MaterialResponse create(Long sectionId, MaterialRequest request) {

        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Section not found: " + sectionId));

        Material material = new Material();
        material.setTitle(request.title());
        material.setContentType(request.contentType());
        material.setContentUrl(request.contentUrl());
        material.setSection(section);

        return mapToResponse(materialRepository.save(material));
    }

    public MaterialResponse update(Long id, MaterialRequest request) {

        Material material = materialRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Material not found: " + id));

        material.setTitle(request.title());
        material.setContentType(request.contentType());
        material.setContentUrl(request.contentUrl());

        return mapToResponse(materialRepository.save(material));
    }

    public void delete(Long id) {
        if (!materialRepository.existsById(id)) {
            throw new ResourceNotFoundException("Material not found: " + id);
        }
        materialRepository.deleteById(id);
    }

    private MaterialResponse mapToResponse(Material material) {
        return new MaterialResponse(
                material.getId(),
                material.getTitle(),
                material.getContentType(),
                material.getContentUrl(),
                material.getSection().getId()
        );
    }
}
