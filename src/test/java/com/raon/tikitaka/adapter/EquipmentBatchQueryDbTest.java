package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.user.out.EquipmentJpaRepository;
import com.raon.tikitaka.adapter.user.out.EquipmentPersistenceAdapter;
import com.raon.tikitaka.adapter.user.out.EquippedColorJpaRepository;
import com.raon.tikitaka.adapter.user.out.EquippedColorPersistenceAdapter;
import com.raon.tikitaka.application.color.out.ColorRepositoryPort;
import com.raon.tikitaka.application.equipment.EquipmentService;
import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.domain.userItem.Equipment;
import com.raon.tikitaka.domain.userItem.EquippedColor;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 여러 유저의 착용 상품·색 일괄 조회를 실제 PostgreSQL에서 돌려 본다. 유저별로 정확히 묶이는지와,
 * 유저 수와 상관없이 쿼리가 두 번(상품, 색)으로 끝나는지(Equipment.user LAZY 초기화 쿼리가 없는지) 확인한다.
 * WithdrawnUserQueryTest와 같이 TEST_DATABASE_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class EquipmentBatchQueryDbTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired EntityManager em;
    @Autowired EquipmentJpaRepository equipmentJpaRepository;
    @Autowired EquippedColorJpaRepository equippedColorJpaRepository;

    private EquipmentService equipmentService;
    private Statistics statistics;
    private Location home;
    private Color brownHair;
    private Color blueEyes;

    @BeforeEach
    void setUp() {
        // 일괄 조회는 착용 포트 두 개만 쓴다. 나머지 포트가 불리면 mock이라 쿼리 수에도 잡히지 않는다
        equipmentService = new EquipmentService(mock(UserRepositoryPort.class), mock(InventoryRepositoryPort.class),
                new EquipmentPersistenceAdapter(equipmentJpaRepository),
                new EquippedColorPersistenceAdapter(equippedColorJpaRepository), mock(ColorRepositoryPort.class));
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

        home = location("해운대구");
        product("body-01", "BODY");
        product("hair-front-01", "HAIR_FRONT");
        product("eyes-01", "EYES");
        brownHair = Color.of(ColorGroup.HAIR, "#8B4513", "갈색");
        blueEyes = Color.of(ColorGroup.EYES, "#0000FF", "파랑");
        em.persist(brownHair);
        em.persist(blueEyes);
    }

    @Test
    void 여러_유저의_상품과_색이_유저별로_묶인다() {
        Users a = user("a");
        Users b = user("b");
        Users none = user("none");
        equip(a, "body-01");
        equip(a, "hair-front-01");
        color(a, ColorGroup.HAIR, brownHair);
        equip(b, "eyes-01");
        color(b, ColorGroup.EYES, blueEyes);
        flushAndClear();

        Map<UUID, EquippedItems> result =
                equipmentService.getEquippedItemsByUserIds(List.of(a.getUserId(), b.getUserId(), none.getUserId()));

        assertThat(result).containsOnlyKeys(a.getUserId(), b.getUserId(), none.getUserId());
        assertThat(result.get(a.getUserId()).products()).extracting(Product::getProductId)
                .containsExactlyInAnyOrder("body-01", "hair-front-01");
        assertThat(result.get(a.getUserId()).colors()).containsOnlyKeys(ColorGroup.HAIR);
        assertThat(result.get(a.getUserId()).colors().get(ColorGroup.HAIR).getColorCode()).isEqualTo("#8B4513");
        assertThat(result.get(b.getUserId()).products()).extracting(Product::getProductId).containsExactly("eyes-01");
        assertThat(result.get(b.getUserId()).colors().get(ColorGroup.EYES).getColorCode()).isEqualTo("#0000FF");
        // 착용한 것이 없는 유저도 빈 값으로 들어간다
        assertThat(result.get(none.getUserId()).products()).isEmpty();
        assertThat(result.get(none.getUserId()).colors()).isEmpty();
    }

    @Test
    void 착용_정보가_없는_유저는_쿼리_결과에_없다() {
        Users a = user("a");
        Users none = user("none");
        equip(a, "body-01");
        color(a, ColorGroup.HAIR, brownHair);
        flushAndClear();

        List<Equipment> equipments = equipmentJpaRepository.findAllByUserIdsWithProduct(
                List.of(a.getUserId(), none.getUserId()));
        List<EquippedColor> colors = equippedColorJpaRepository.findAllByUserIdsWithColor(
                List.of(a.getUserId(), none.getUserId()));

        assertThat(equipments).extracting(e -> e.getUser().getUserId()).containsOnly(a.getUserId());
        assertThat(colors).extracting(c -> c.getUser().getUserId()).containsOnly(a.getUserId());
    }

    @Test
    void 유저_10명이어도_쿼리는_상품과_색_두_번이고_유저_초기화_쿼리가_없다() {
        List<UUID> userIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Users user = user("u" + i);
            equip(user, "body-01");
            equip(user, "eyes-01");
            color(user, ColorGroup.HAIR, brownHair);
            userIds.add(user.getUserId());
        }
        flushAndClear();
        statistics.clear();

        Map<UUID, EquippedItems> result = equipmentService.getEquippedItemsByUserIds(userIds);

        assertThat(result).hasSize(10);
        assertThat(result.values()).allSatisfy(items -> {
            assertThat(items.products()).hasSize(2);
            assertThat(items.colors()).containsOnlyKeys(ColorGroup.HAIR);
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        // 영속성 컨텍스트를 비운 뒤라 Equipment.user는 프록시다. id만 읽었으므로 Users가 로드되지 않아야 한다
        assertThat(statistics.getEntityStatistics(Users.class.getName()).getLoadCount()).isZero();
    }

    @Test
    void 같은_id가_여러_번_와도_결과는_하나다() {
        Users a = user("a");
        equip(a, "body-01");
        flushAndClear();
        statistics.clear();

        Map<UUID, EquippedItems> result =
                equipmentService.getEquippedItemsByUserIds(List.of(a.getUserId(), a.getUserId(), a.getUserId()));

        assertThat(result).containsOnlyKeys(a.getUserId());
        assertThat(result.get(a.getUserId()).products()).hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void 빈_id_목록이면_쿼리를_보내지_않는다() {
        flushAndClear();
        statistics.clear();

        Map<UUID, EquippedItems> result = equipmentService.getEquippedItemsByUserIds(List.of());

        assertThat(result).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void 빈_id_목록으로_쿼리를_직접_불러도_빈_결과다() {
        assertThat(equipmentJpaRepository.findAllByUserIdsWithProduct(List.of())).isEmpty();
        assertThat(equippedColorJpaRepository.findAllByUserIdsWithColor(List.of())).isEmpty();
    }

    // ===== helpers =====

    private Location location(String name) {
        em.createNativeQuery("insert into location (city_name, location_name) values ('부산광역시', :name)")
                .setParameter("name", name)
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select location_id from location where location_name = :name")
                .setParameter("name", name)
                .getSingleResult()).longValue();
        return em.find(Location.class, id);
    }

    /** Product는 정적 생성 메서드가 없어 다른 DB 테스트의 location처럼 네이티브 insert로 넣는다 */
    private void product(String productId, String productType) {
        em.createNativeQuery("insert into product (product_id, product_name, product_image, product_type, price, is_active) "
                        + "values (:id, :id, :image, :type, 100, true)")
                .setParameter("id", productId)
                .setParameter("image", productId + ".png")
                .setParameter("type", productType)
                .executeUpdate();
    }

    private Users user(String name) {
        Users user = Users.of(name, LoginProvider.KAKAO, name + "-provider", home);
        em.persist(user);
        return user;
    }

    private void equip(Users user, String productId) {
        em.persist(Equipment.of(user, em.find(Product.class, productId)));
    }

    private void color(Users user, ColorGroup group, Color color) {
        em.persist(EquippedColor.of(user, group, color));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
