package com.wyb.sseat.vo;

import com.wyb.sseat.entity.Role;
import com.wyb.sseat.entity.User;
import lombok.Data;

/**
 * 返回给前端的用户信息视图（VO）。
 * 绝不能直接返回 User 实体——以后万一去掉 @JsonIgnore 密码就泄露了，
 * VO 是专门"挑能看的字段"给前端的一层。
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String phone;
    private String email;
    private String address;
    private String intro;
    private String roleCode;
    private String roleName;
    private String menus;   // 该角色可见的后台菜单，前端据此显示菜单
    private Integer status;

    public static UserVO of(User u, Role r) {
        UserVO v = new UserVO();
        v.id = u.getId();
        v.username = u.getUsername();
        v.nickname = u.getNickname();
        v.avatar = u.getAvatar();
        v.gender = u.getGender();
        v.phone = u.getPhone();
        v.email = u.getEmail();
        v.address = u.getAddress();
        v.intro = u.getIntro();
        v.roleCode = u.getRoleCode();
        v.status = u.getStatus();
        if (r != null) {
            v.roleName = r.getRoleName();
            v.menus = r.getMenus();
        }
        return v;
    }
}
