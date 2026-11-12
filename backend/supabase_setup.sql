-- MYRA wallpapers: run this ONCE in Supabase -> SQL Editor -> New query -> paste -> RUN.
-- Admin = the account with this e-mail. Everyone else can only READ published wallpapers.

-- 1) table ---------------------------------------------------------------------------
create table if not exists public.wallpapers (
  id           text primary key,
  title        text not null,
  description  text default '',
  category     text default 'General',
  video_url    text not null,
  thumb_url    text not null,
  video_path   text default '',
  thumb_path   text default '',
  size_bytes   bigint default 0,
  created_at   bigint not null,
  downloads    bigint default 0,
  views        bigint default 0,
  published    boolean default false
);

alter table public.wallpapers enable row level security;

drop policy if exists "public read published" on public.wallpapers;
create policy "public read published" on public.wallpapers
  for select using (published = true);

drop policy if exists "admin full access" on public.wallpapers;
create policy "admin full access" on public.wallpapers
  for all to authenticated
  using      ((auth.jwt() ->> 'email') = 'umarmalang496@gmail.com')
  with check ((auth.jwt() ->> 'email') = 'umarmalang496@gmail.com');

-- 2) safe statistics counter (+1 only, only downloads/views, only published items) ----
create or replace function public.bump_stat(wp_id text, field text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if field = 'downloads' then
    update public.wallpapers set downloads = downloads + 1 where id = wp_id and published = true;
  elsif field = 'views' then
    update public.wallpapers set views = views + 1 where id = wp_id and published = true;
  end if;
end $$;
grant execute on function public.bump_stat(text, text) to anon, authenticated;

-- 3) storage bucket (public read, 50 MB per file = Supabase free-plan limit) ----------
insert into storage.buckets (id, name, public, file_size_limit)
values ('wallpapers', 'wallpapers', true, 52428800)
on conflict (id) do update set public = true, file_size_limit = 52428800;

drop policy if exists "wallpapers admin insert" on storage.objects;
create policy "wallpapers admin insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'wallpapers' and (auth.jwt() ->> 'email') = 'umarmalang496@gmail.com');

drop policy if exists "wallpapers admin update" on storage.objects;
create policy "wallpapers admin update" on storage.objects
  for update to authenticated
  using (bucket_id = 'wallpapers' and (auth.jwt() ->> 'email') = 'umarmalang496@gmail.com');

drop policy if exists "wallpapers admin delete" on storage.objects;
create policy "wallpapers admin delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'wallpapers' and (auth.jwt() ->> 'email') = 'umarmalang496@gmail.com');
