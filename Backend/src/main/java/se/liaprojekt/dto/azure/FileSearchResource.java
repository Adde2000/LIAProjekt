package se.liaprojekt.dto.azure;

import java.util.List;

public record FileSearchResource(

        List<String> vector_store_ids

) {
}