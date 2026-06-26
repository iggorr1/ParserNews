alter table news_sources add column if not exists tier varchar(16) not null default 'BROAD';
