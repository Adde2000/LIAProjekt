package se.liaprojekt.dto.azure;

import java.util.List;

public record AzureMessageData(

        String id,

        String role,

        List<AzureMessageContent> content

) {
}