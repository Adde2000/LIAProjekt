package se.liaprojekt.service;

import com.azure.core.credential.TokenCredential;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.stereotype.Service;

@Service
public class BlobStorageService {

    private final BlobServiceClient blobServiceClient;

    public BlobStorageService(TokenCredential credential) {
        this.blobServiceClient =
                new BlobServiceClientBuilder()
                        .endpoint("https://<your-storage>.blob.core.windows.net")
                        .credential(credential)
                        .buildClient();
    }

    public String downloadText(String container, String fileName) {
        BlobClient client = blobServiceClient
                .getBlobContainerClient(container)
                .getBlobClient(fileName);

        return new String(client.downloadContent().toBytes());
    }

    public byte[] downloadPdf(String container, String fileName) {
        BlobClient client = blobServiceClient
                .getBlobContainerClient(container)
                .getBlobClient(fileName);

        return client.downloadContent().toBytes();
    }
}