import java.util.Random

fun main() {
    val random = Random()
    val sb = StringBuilder()
    
    val tags = listOf("ActivityManager", "WindowManager", "ViewRootImpl", "SystemServer", "InputDispatcher", "BatteryService", "WifiService", "PowerManagerService")
    val pid = 1234
    val tid = 5678
    
    fun logLine(time: String, level: String, tag: String, message: String): String {
        return "$time  $pid  $tid $level $tag: $message\n"
    }

    var currentTime = 1713322800000L // 2026-04-17 12:00:00
    
    fun getFormattedTime(time: Long): String {
        val date = java.util.Date(time)
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS")
        return sdf.format(date)
    }

    val orderIds = (1..50).map { "flow_%03d".format(it) }
    val activeFlows = mutableMapOf<String, Int>() // ID to step index
    
    val steps = listOf(
        "Order %s started",
        "Transaction verified for %s",
        "Payment for %s processed",
        "Order %s shipped",
        "Order %s completed"
    )
    val stepTags = listOf("OrderEngine", "PaymentProcessor", "PaymentProcessor", "LogPulse", "LogPulse")

    for (i in 1..1500) {
        currentTime += random.nextInt(500) + 50
        val timeStr = getFormattedTime(currentTime)
        
        // Randomly start or progress a flow
        if (random.nextDouble() < 0.1 && activeFlows.size < 10) {
            val nextId = orderIds.find { it !in activeFlows }
            if (nextId != null) activeFlows[nextId] = 0
        }
        
        // Progress flows
        if (random.nextDouble() < 0.15 && activeFlows.isNotEmpty()) {
            val idToProgress = activeFlows.keys.shuffled().first()
            val stepIdx = activeFlows[idToProgress]!!
            
            sb.append(logLine(timeStr, "I", stepTags[stepIdx], steps[stepIdx].format(idToProgress)))
            
            if (stepIdx < steps.size - 1) {
                // Occasional failure
                if (idToProgress == "flow_007" && stepIdx == 1) {
                    currentTime += 10
                    sb.append(logLine(getFormattedTime(currentTime), "E", "PaymentProcessor", "Insufficient funds for $idToProgress"))
                    activeFlows.remove(idToProgress)
                } else {
                    activeFlows[idToProgress] = stepIdx + 1
                }
            } else {
                activeFlows.remove(idToProgress)
            }
            continue
        }

        // Random Android Runtime Exception
        if (i == 450) {
            sb.append(logLine(timeStr, "E", "AndroidRuntime", "FATAL EXCEPTION: main"))
            sb.append(logLine(timeStr, "E", "AndroidRuntime", "Process: com.antdev.logpulse, PID: $pid"))
            sb.append(logLine(timeStr, "E", "AndroidRuntime", "java.lang.NullPointerException: Attempt to invoke virtual method 'void java.lang.String.trim()' on a null object reference"))
            sb.append(logLine(timeStr, "E", "AndroidRuntime", "\tat com.antdev.logpulse.presentation.viewmodel.LogViewModel.onLogSelected(LogViewModel.kt:157)"))
            sb.append(logLine(timeStr, "E", "AndroidRuntime", "\tat com.antdev.logpulse.presentation.viewmodel.LogViewModel\$jumpToLog\$1.invokeSuspend(LogViewModel.kt:164)"))
            continue
        }

        // Random Noise
        val tag = tags[random.nextInt(tags.size)]
        val level = if (random.nextDouble() < 0.05) "W" else if (random.nextDouble() < 0.02) "E" else "D"
        val msg = when(tag) {
            "ActivityManager" -> "Displaying activity com.antdev.logpulse/.MainActivity: +450ms"
            "WindowManager" -> "Relayout Window{f3e4e5 m=false}: viewVisibility=0"
            "ViewRootImpl" -> "MSG_RESIZED: frame=[0,0][1400,900] ci=[0,0][0,0] vi=[0,0][0,0] or=1"
            "InputDispatcher" -> "Delivered touch event to com.antdev.logpulse/com.antdev.logpulse.MainActivity"
            else -> "Background maintenance task execution: ${random.nextInt(100)}%"
        }
        sb.append(logLine(timeStr, level, tag, msg))
    }

    java.io.File("logs.txt").writeText(sb.toString())
    println("Generated 1500 lines to logs.txt")
}

main()
