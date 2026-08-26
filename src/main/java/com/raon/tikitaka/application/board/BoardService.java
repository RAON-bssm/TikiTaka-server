package com.raon.tikitaka.application.board;

import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.board.out.BoardRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService implements GetBoardUseCase {

    private final BoardRepositoryPort boardRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    @Override
    public BoardListResult getBoards(UUID userId) {
        List<Board> boards = boardRepositoryPort.findAllActiveBoards(LocalDateTime.now());
        // 비로그인 조회를 허용한다. principal이 없으면 userId가 null로 들어오고
        // myLocationId도 null이 되어 모든 게시판의 myMatch가 false로 내려간다
        Long myLocationId = (userId == null) ? null : mainLocationId(userId);
        return new BoardListResult(boards, myLocationId);
    }

    @Override
    public String getMission(Long boardId, UUID userId) {
        Optional<Board> board = boardRepositoryPort
                .findActiveById(boardId, LocalDateTime.now(), mainLocationId(userId));
        if (board.isEmpty()) {
            // 없는 ID인지 종료된 라운드인지 남의 동네인지 구분되는 메시지를 남긴다
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "종료되었거나 접근할 수 없는 게시판입니다.");
        }
        return board.get().getMatch().getMission();
    }

    private Long mainLocationId(UUID userId) {
        Optional<Users> user = userRepositoryPort.findByIdWithLocations(userId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return user.get().getMainLocation().getLocationId();    // main_location은 NOT NULL
    }
}
