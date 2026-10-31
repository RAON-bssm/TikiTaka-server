package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.UsedPhoto;

public interface UsedPhotoPort {

    boolean existsByHash(String photoHash);

    void save(UsedPhoto usedPhoto);
}
