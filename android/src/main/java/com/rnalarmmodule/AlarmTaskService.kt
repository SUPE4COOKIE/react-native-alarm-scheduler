package com.rnalarmmodule

import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class AlarmTaskService : HeadlessJsTaskService() {
    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        return intent?.extras?.let { extras ->
            val id = extras.getString("id")
            val title = extras.getString("title")
            val body = extras.getString("body")
            val action = extras.getString("action") ?: "RING"

            val data = Arguments.createMap().apply {
                putString("id", id)
                putString("title", title)
                putString("body", body)
                putString("action", action)
            }

            HeadlessJsTaskConfig(
                "RNAlarmTask",
                data,
                0, // timeout for the task (0 means no timeout)
                true // allowed in foreground
            )
        }
    }
}
