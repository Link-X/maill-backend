-- 演出搜索索引迁移
-- 配合 ShowMapper.xml 的 LIKE 前缀匹配(xxx%),让搜索查询能走 B-Tree 索引,避免全表扫描.
--
-- 注意事项:
--   1. ALTER TABLE 在大表上是阻塞操作,生产建议使用 pt-online-schema-change 或 gh-ost
--   2. category 区分度较低(只有几个分类),单独建索引收益有限,与 name 组合作用最大
--   3. venue 区分度中等,值得单独索引
--
-- 执行: mysql -u root -p ticket_system < migration_show_search_index.sql

ALTER TABLE `show`
    ADD KEY idx_name (name),
    ADD KEY idx_venue (venue),
    ADD KEY idx_category (category);
