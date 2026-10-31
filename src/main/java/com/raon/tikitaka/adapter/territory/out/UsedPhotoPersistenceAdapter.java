package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.UsedPhotoPort;
import com.raon.tikitaka.domain.territory.UsedPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsedPhotoPersistenceAdapter implements UsedPhotoPort {

    private final UsedPhotoJpaRepository usedPhotoJpaRepository;

    @Override
    public boolean existsByHash(String photoHash) {
        return usedPhotoJpaRepository.existsById(photoHash);
    }

    /** id(해시)를 직접 넣는 엔티티라 save는 merge(select 후 insert)로 동작한다. 중복 여부는 호출 전에 existsByHash로 거른다 */
    @Override
    public void save(UsedPhoto usedPhoto) {
        usedPhotoJpaRepository.save(usedPhoto);
    }
}
