# MYRA – Backend & Admin Panel (SUPABASE, free plan)

Firebase Storage needs a paid plan, so wallpapers now use **Supabase** (Auth + database + file storage, all free).
(The old `firebase.json / firestore.rules / storage.rules / admin/` files are no longer used - ignore them.)

## Setup (once, phone browser is fine) – https://supabase.com/dashboard
1. Open your project → **SQL Editor → New query** → paste the whole file `backend/supabase_setup.sql` → **Run**.
   (Creates the table, the policies, the `wallpapers` storage bucket.)
2. **Authentication → Users → Add user → Create new user**: email `umarmalang496@gmail.com`, your password,
   and tick **Auto Confirm User**.
3. **Project Settings → API**: copy **Project URL** and the **anon / publishable key**.
4. Put them in `app/src/main/java/com/myra/assistant/wallpaper/CloudConfig.kt`
   (`SUPABASE_URL`, `SUPABASE_KEY`) and rebuild the APK.

## Using it
App → Settings (bottom) → **Admin Panel** → login → pick video (+ optional thumbnail) → Title/Description/Category
→ UPLOAD & SAVE. Published wallpapers appear for every user (gallery refreshes on open and every 45 s).

## Limits to know (Supabase free plan)
* Max **50 MB per file** → compress videos (e.g. 720p, 10-20 s loop).
* 1 GB total storage, 5 GB/month traffic.
* A free project **pauses after ~7 days with no activity**; open the dashboard and click Restore. Normal app use counts as activity.
* The key in the APK is public by design; only `umarmalang496@gmail.com` can write (policies in the SQL file).
