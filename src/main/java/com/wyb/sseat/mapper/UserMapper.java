package com.wyb.sseat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wyb.sseat.entity.User;

/**
 * 用户表数据库接口：继承 BaseMapper 后自动拥有单表 CRUD 方法，
 * 无需写任何 SQL 和 XML（insertById、selectById、selectList、updateById、deleteById 等）。
 */
public interface UserMapper extends BaseMapper<User> {
}
