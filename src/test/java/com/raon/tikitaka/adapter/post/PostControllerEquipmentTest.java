package com.raon.tikitaka.adapter.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raon.tikitaka.adapter.equipment.EquipmentController;
import com.raon.tikitaka.adapter.ranking.RankingQueryController;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.equipment.in.EquipItemUseCase;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.post.PostSummary;
import com.raon.tikitaka.application.post.in.CheckPostUploadLimitUseCase;
import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.LikePostUseCase;
import com.raon.tikitaka.application.post.in.UnlikePostUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.application.ranking.UserRankingResult;
import com.raon.tikitaka.application.ranking.in.GetLocationRankingUseCase;
import com.raon.tikitaka.application.ranking.in.GetUserRankingUseCase;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 글 목록과 개인 랭킹 응답의 user_equipment가 GET /api/equipment/{userId} 응답과 같은 모양·값인지 본다.
 * 직렬화 설정은 application.yaml의 spring.jackson(SNAKE_CASE, non_null)과 같게 맞춘다.
 */
class PostControllerEquipmentTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private final GetPostListUseCase getPostListUseCase = mock(GetPostListUseCase.class);
    private final GetUserRankingUseCase getUserRankingUseCase = mock(GetUserRankingUseCase.class);
    private final GetEquippedItemsUseCase getEquippedItemsUseCase = mock(GetEquippedItemsUseCase.class);
    private PostController postController;
    private RankingQueryController rankingQueryController;
    private EquipmentController equipmentController;

    private final UUID dressedId = UUID.randomUUID();
    private final UUID plainId = UUID.randomUUID();
    private EquippedItems dressed;

    @BeforeEach
    void setUp() {
        postController = new PostController(getPostListUseCase, mock(GetPostDetailUseCase.class),
                mock(CreatePostUseCase.class), mock(UpdatePostUseCase.class), mock(DeletePostUseCase.class),
                mock(LikePostUseCase.class), mock(UnlikePostUseCase.class), mock(StorageUseCase.class),
                mock(ReviewUseCase.class), mock(GetBoardUseCase.class), new RequestGuard(),
                new PostReviewProperties(false, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)),
                mock(CheckPostUploadLimitUseCase.class));
        rankingQueryController = new RankingQueryController(mock(GetLocationRankingUseCase.class), getUserRankingUseCase);
        equipmentController = new EquipmentController(mock(EquipItemUseCase.class), getEquippedItemsUseCase);

        Color brown = color(ColorGroup.HAIR, "#8B4513");
        Color blue = color(ColorGroup.EYES, "#0000FF");
        dressed = new EquippedItems(
                List.of(product("body-01", ProductType.BODY), product("hair-front-01", ProductType.HAIR_FRONT),
                        product("eyes-01", ProductType.EYES)),
                Map.of(ColorGroup.HAIR, brown, ColorGroup.EYES, blue));
        when(getEquippedItemsUseCase.getEquippedItems(dressedId)).thenReturn(dressed);
    }

    @Test
    void 글_목록의_user_equipment는_단건_장비_조회_응답과_같다() {
        Post dressedPost = post(dressedId);
        Post plainPost = post(plainId);
        when(getPostListUseCase.getPosts(3L, null)).thenReturn(List.of(
                new PostSummary(dressedPost, 0, false, null, null, null, dressed),
                new PostSummary(plainPost, 0, false, null, null, null, EquippedItems.empty())));

        JsonNode posts = json(postController.getPosts(null, 3L, null, null)).get("data").get("post");
        JsonNode single = json(equipmentController.getEquippedItemsOf(dressedId)).get("data");

        JsonNode equipment = posts.get(0).get("user_equipment");
        assertThat(equipment).isEqualTo(single);
        assertThat(posts.get(0).has("userEquipment")).isFalse();
        assertThat(equipment.get("body").get("product_id").asString()).isEqualTo("body-01");
        assertThat(equipment.get("body").get("product_name").asString()).isEqualTo("body-01 이름");
        assertThat(equipment.get("body").get("product_image").asString()).isEqualTo("body-01.png");
        assertThat(equipment.get("hair_front").get("color").asString()).isEqualTo("#8B4513");
        assertThat(equipment.get("eyes").get("color").asString()).isEqualTo("#0000FF");
        assertThat(equipment.get("hair_color").asString()).isEqualTo("#8B4513");
        assertThat(equipment.get("eyes_color").asString()).isEqualTo("#0000FF");
        // 착용한 것이 없으면 필드는 있고 내용은 빈 객체다
        assertThat(posts.get(1).has("user_equipment")).isTrue();
        assertThat(posts.get(1).get("user_equipment").isEmpty()).isTrue();
    }

    @Test
    void 랭킹의_user_ranking과_my_ranking에_user_equipment가_들어간다() {
        UserRankRow dressedRow = row(dressedId, 1);
        UserRankRow plainRow = row(plainId, 2);
        when(getUserRankingUseCase.getUserRanking(plainId)).thenReturn(new UserRankingResult(
                List.of(dressedRow, plainRow), plainRow, Map.of(dressedId, dressed, plainId, EquippedItems.empty())));

        JsonNode data = json(rankingQueryController.getUserRanking(plainId)).get("data");
        JsonNode single = json(equipmentController.getEquippedItemsOf(dressedId)).get("data");

        JsonNode ranking = data.get("user_ranking");
        assertThat(ranking.get(0).get("user_id").asString()).isEqualTo(dressedId.toString());
        assertThat(ranking.get(0).get("user_equipment")).isEqualTo(single);
        assertThat(ranking.get(0).get("user_rank").asInt()).isEqualTo(1);
        assertThat(ranking.get(1).get("user_equipment").isEmpty()).isTrue();
        assertThat(data.get("my_ranking").get("user_equipment").isEmpty()).isTrue();
    }

    @Test
    void 랭킹에_미참여면_my_ranking이_생략되고_목록은_정상이다() {
        UserRankRow dressedRow = row(dressedId, 1);
        when(getUserRankingUseCase.getUserRanking(plainId)).thenReturn(new UserRankingResult(
                List.of(dressedRow), null, Map.of(dressedId, dressed)));

        JsonNode data = json(rankingQueryController.getUserRanking(plainId)).get("data");

        assertThat(data.has("my_ranking")).isFalse();
        assertThat(data.get("user_ranking").get(0).get("user_equipment").get("hair_color").asString())
                .isEqualTo("#8B4513");
    }

    // ===== helpers =====

    private JsonNode json(Object response) {
        return jsonMapper.readTree(jsonMapper.writeValueAsString(response));
    }

    private static Product product(String id, ProductType type) {
        Product product = mock(Product.class);
        lenient().when(product.getProductId()).thenReturn(id);
        lenient().when(product.getProductName()).thenReturn(id + " 이름");
        lenient().when(product.getProductImage()).thenReturn(id + ".png");
        lenient().when(product.getProductType()).thenReturn(type);
        return product;
    }

    private static Color color(ColorGroup group, String code) {
        Color color = mock(Color.class);
        lenient().when(color.getColorGroup()).thenReturn(group);
        lenient().when(color.getColorCode()).thenReturn(code);
        return color;
    }

    private static UserRankRow row(UUID userId, int rank) {
        UserRankRow row = mock(UserRankRow.class);
        lenient().when(row.getUserId()).thenReturn(userId);
        lenient().when(row.getUserName()).thenReturn("유저" + rank);
        lenient().when(row.getUserRank()).thenReturn(rank);
        lenient().when(row.getUserScore()).thenReturn(1000L - rank);
        return row;
    }

    private static Post post(UUID authorId) {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(authorId);
        lenient().when(author.getUserName()).thenReturn("작성자");
        Location team = mock(Location.class);
        lenient().when(team.getCityName()).thenReturn("부산광역시");
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getStatus()).thenReturn(PostStatus.DONE);
        lenient().when(post.getTeamLocation()).thenReturn(team);
        return post;
    }
}
