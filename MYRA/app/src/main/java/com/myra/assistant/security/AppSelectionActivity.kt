package com.myra.assistant.security

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import kotlinx.coroutines.*

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        recyclerView = findViewById(R.id.appsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        loadApps()
    }

    private fun loadApps() {
        activityScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val lockedApps =
                    SecurityManager.getLockedPackages(this@AppSelectionActivity)

                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter {
                        (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                                it.packageName != packageName
                    }
                    .map {
                        AppInfo(
                            name = it.loadLabel(pm).toString(),
                            packageName = it.packageName,
                            icon = it.loadIcon(pm),
                            isLocked = lockedApps.contains(it.packageName)
                        )
                    }
                    .sortedBy { it.name.lowercase() }
            }

            recyclerView.adapter = AppAdapter(apps)
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable,
        var isLocked: Boolean
    )

    inner class AppAdapter(
        private val apps: List<AppInfo>
    ) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        inner class AppViewHolder(view: View)
            : RecyclerView.ViewHolder(view) {

            val icon: ImageView = view.findViewById(R.id.appIcon)
            val name: TextView = view.findViewById(R.id.appName)
            val checkbox: CheckBox = view.findViewById(R.id.appCheckbox)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_app_selection,
                    parent,
                    false
                )
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: AppViewHolder,
            position: Int
        ) {
            val app = apps[position]

            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.name

            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isLocked

            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                updateLock(app, isChecked)
            }

            holder.itemView.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            }
        }

        private fun updateLock(
            app: AppInfo,
            locked: Boolean
        ) {
            app.isLocked = locked

            if (locked) {
                SecurityManager.addLockedPackage(
                    this@AppSelectionActivity,
                    app.packageName
                )
            } else {
                SecurityManager.removeLockedPackage(
                    this@AppSelectionActivity,
                    app.packageName
                )
            }
        }

        override fun getItemCount() = apps.size
    }
}