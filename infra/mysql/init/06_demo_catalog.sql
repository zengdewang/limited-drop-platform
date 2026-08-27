-- 演示商品目录：按品牌 + 名称幂等写入，重复执行不会产生重复商品。
USE drop_product_db;

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'Hermes', 'Birkin 25 Togo', 'Handbag',
       'https://images.unsplash.com/photo-1584917865442-de89df76afd3?auto=format&fit=crop&w=900&q=85',
       'Birkin 25 采用 Togo 小牛皮，皮面纹理清晰，结构挺括且耐日常划痕。25 厘米尺寸适合随身携带，建议避开潮湿、高温与长时间日晒。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'Hermes' AND name = 'Birkin 25 Togo');

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'The Row', 'Soft Margaux 12', 'Handbag',
       'https://images.unsplash.com/photo-1594223274512-ad4803739b7c?auto=format&fit=crop&w=900&q=85',
       'Soft Margaux 12 以柔软粒面皮革和克制轮廓为特点，内部容量适合通勤。收纳时应填充包身并使用防尘袋，避免重压导致变形。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'The Row' AND name = 'Soft Margaux 12');

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'Loro Piana', 'Andre Camp Moc', 'Shoes',
       'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=900&q=85',
       'Andre Camp Moc 使用柔软绒面小牛皮与轻质鞋底，适合休闲通勤。绒面应使用专用软刷清洁，并避免在雨雪环境中长时间穿着。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'Loro Piana' AND name = 'Andre Camp Moc');

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'Bottega Veneta', 'Intrecciato Cassette', 'Accessories',
       'https://images.unsplash.com/photo-1566150905458-1bf1fc113f0d?auto=format&fit=crop&w=900&q=85',
       'Cassette 采用品牌标志性的 Intrecciato 编织工艺，皮革条带形成具有辨识度的方格结构。日常使用应避免与尖锐物体摩擦。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'Bottega Veneta' AND name = 'Intrecciato Cassette');

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'Loewe', 'Puzzle Edge Small', 'Handbag',
       'https://images.unsplash.com/photo-1591561954557-26941169b49e?auto=format&fit=crop&w=900&q=85',
       'Puzzle Edge Small 以几何皮片拼接构成立体包身，可手提或肩背。皮革边缘经过精细处理，建议使用干燥软布进行日常维护。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'Loewe' AND name = 'Puzzle Edge Small');

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'Maison Margiela', 'Tabi Leather Loafers', 'Shoes',
       'https://images.unsplash.com/photo-1560769629-975ec94e6a86?auto=format&fit=crop&w=900&q=85',
       'Tabi 乐福鞋采用分趾轮廓与光滑皮革鞋面，建议初次穿着逐步增加时长。清洁时使用中性皮革护理产品并保持鞋内干燥。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'Maison Margiela' AND name = 'Tabi Leather Loafers');

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'Cartier', 'Panthere Mini Bag', 'Accessories',
       'https://images.unsplash.com/photo-1585488434455-1e7b6b7a18f6?auto=format&fit=crop&w=900&q=85',
       'Panthere Mini Bag 以紧凑包身和金属细节为设计重点，可容纳手机与随身小件。金属部件应避免接触香水和清洁剂。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'Cartier' AND name = 'Panthere Mini Bag');

INSERT INTO product (brand, name, category, image_url, official_doc)
SELECT 'Rimowa', 'Original Cabin', 'Object',
       'https://images.unsplash.com/photo-1553531384-cc64ac80f931?auto=format&fit=crop&w=900&q=85',
       'Original Cabin 采用沟槽铝镁合金外壳与多轮系统，适合短途旅行。铝制外壳会随使用留下痕迹，清洁时应使用清水和软布。'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE brand = 'Rimowa' AND name = 'Original Cabin');
