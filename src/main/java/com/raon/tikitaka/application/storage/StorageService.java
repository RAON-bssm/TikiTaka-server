package com.raon.tikitaka.application.storage;

import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.application.storage.out.ThumbnailGeneratorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService implements StorageUseCase {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(3);
    private static final Duration VIEW_URL_EXPIRATION = Duration.ofMinutes(10);

    /** 일괄 조회 URL 한 번에 받을 수 있는 key 수. 글 목록 한 페이지 상한(50)과 같다 */
    private static final int MAX_VIEW_URL_KEYS = 50;

    /** 썸네일 key는 원본과 달리 사용자가 정한 파일명을 넣지 않는다 */
    private static final String THUMBNAIL_KEY_PREFIX = "thumbnails/";
    private static final String THUMBNAIL_KEY_SUFFIX = ".jpg";
    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";

    private final StoragePort storagePort;
    private final ThumbnailGeneratorPort thumbnailGeneratorPort;

    @Override
    public URL issueUploadUrl(String fileName) {
        String key = UUID.randomUUID() + "-" + fileName;
        return storagePort.issueUploadUrl(key, UPLOAD_URL_EXPIRATION);
    }

    @Override
    public URL issueViewUrl(String key) {
        return storagePort.issueViewUrl(key, VIEW_URL_EXPIRATION);
    }

    /**
     * 개수와 빈 key는 중복을 빼기 전, 요청받은 그대로 센다. 서명은 S3에 요청하지 않는 로컬 계산이라
     * key마다 issueViewUrl을 부르면 된다.
     */
    @Override
    public List<ViewUrl> issueViewUrls(Collection<String> keys) {
        if (keys.isEmpty() || keys.size() > MAX_VIEW_URL_KEYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "key는 1개 이상 " + MAX_VIEW_URL_KEYS + "개 이하여야 합니다.");
        }
        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 key는 보낼 수 없습니다.");
            }
            uniqueKeys.add(key);
        }

        List<ViewUrl> viewUrls = new ArrayList<>();
        for (String key : uniqueKeys) {
            viewUrls.add(new ViewUrl(key, issueViewUrl(key)));
        }
        return viewUrls;
    }

    @Override
    public String uploadImage(byte[] content, String fileName, String contentType) {
        String key = UUID.randomUUID() + "-" + fileName;
        storagePort.upload(key, content, contentType);
        return key;
    }

    /**
     * 원본을 먼저 올린다. 원본이 실패하면 썸네일은 만들지 않는다.
     */
    @Override
    public UploadedImage uploadImageWithThumbnail(byte[] content, String fileName, String contentType) {
        String key = uploadImage(content, fileName, contentType);
        return new UploadedImage(key, uploadThumbnail(content));
    }

    private String uploadThumbnail(byte[] content) {
        try {
            Optional<byte[]> thumbnail = thumbnailGeneratorPort.createThumbnail(content);
            if (thumbnail.isEmpty()) {
                return null;
            }
            String key = THUMBNAIL_KEY_PREFIX + UUID.randomUUID() + THUMBNAIL_KEY_SUFFIX;
            storagePort.upload(key, thumbnail.get(), THUMBNAIL_CONTENT_TYPE);
            return key;
        } catch (RuntimeException e) {
            log.warn("썸네일 업로드 실패: error={}", e.getClass().getName());
            return null;
        }
    }
}
