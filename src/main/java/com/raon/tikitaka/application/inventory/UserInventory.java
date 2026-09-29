package com.raon.tikitaka.application.inventory;

import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.Inventory;

import java.util.List;
import java.util.Map;

public record UserInventory(List<Inventory> inventoryList, List<Equipment> equipmentList, Map<ColorGroup, Color> colors) {
}
