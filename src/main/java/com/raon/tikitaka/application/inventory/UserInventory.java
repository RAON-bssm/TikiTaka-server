package com.raon.tikitaka.application.inventory;

import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.Inventory;

import java.util.List;

public record UserInventory(List<Inventory> inventoryList, List<Equipment> equipmentList) {
}
