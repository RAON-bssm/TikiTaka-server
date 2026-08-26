package com.raon.tikitaka.application.equipment.in;

import com.raon.tikitaka.domain.product.Product;

import java.util.List;
import java.util.UUID;

public interface GetEquippedItemsUseCase {

    List<Product> getEquippedItems(UUID userId);
}
