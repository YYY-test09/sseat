package com.wyb.sseat.controller.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.dto.AuthDto;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.service.AuthService;
import com.wyb.sseat.service.ProfileService;
import com.wyb.sseat.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端：个人信息管理（所有登录用户可访问）。
 * 注意 @RequestBody 直接复用 User 实体做"部分字段更新"：
 * ProfileService.updateProfile 会把不许用户自己改的字段强制置 null，防越权。
 */
@Tag(name="06-用户端-个人中心")
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final AuthService authService;

    @GetMapping
    public Result<UserVO> info() {
        return Result.ok(authService.me(CurrentUser.id()));
    }

    @PutMapping
    public Result<Void> update(@RequestBody User form) {
        profileService.updateProfile(CurrentUser.id(), form);
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody AuthDto.ChangePasswordReq req) {
        profileService.changePassword(CurrentUser.id(), req);
        return Result.ok();
    }
}
