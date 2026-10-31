package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.UsedPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedPhotoJpaRepository extends JpaRepository<UsedPhoto, String> {
}
