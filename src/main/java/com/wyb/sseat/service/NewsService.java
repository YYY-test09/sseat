package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.entity.News;
import com.wyb.sseat.mapper.NewsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 资讯业务：管理端增删改 + 前台按类型筛选浏览（详情浏览量+1） */
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsMapper newsMapper;

    public Page<News> page(long pageNo, long size, String keyword, String type) {
        return newsMapper.selectPage(new Page<>(pageNo, size),
                new LambdaQueryWrapper<News>()
                        .like(keyword != null && !keyword.isBlank(), News::getTitle, keyword)
                        .eq(type != null && !type.isBlank(), News::getType, type)
                        .orderByDesc(News::getId)); // 按时间倒序（id 自增即最新在前）
    }

    /**
     * 详情。
     * @param countView true=前台浏览，浏览量 +1；false=管理端编辑回显，不加
     */
    public News detail(Long id, boolean countView) {
        News news = newsMapper.selectById(id);
        if (news == null) throw new BizException("资讯不存在");
        if (countView) {
            newsMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<News>lambdaUpdate()
                    .setSql("view_count = view_count + 1").eq(News::getId, id));
            news.setViewCount(news.getViewCount() + 1);
        }
        return news;
    }

    public void save(News news) {
        if (news.getTitle() == null || news.getTitle().isBlank()) throw new BizException("请输入标题");
        if (news.getType() == null || news.getType().isBlank()) throw new BizException("请选择资讯类型");
        if (news.getId() == null) {
            if (news.getAuthor() == null || news.getAuthor().isBlank()) news.setAuthor("管理员");
            newsMapper.insert(news);
        } else {
            if (newsMapper.selectById(news.getId()) == null) throw new BizException("资讯不存在");
            newsMapper.updateById(news);
        }
    }

    public void delete(Long id) {
        newsMapper.deleteById(id);
    }
}
