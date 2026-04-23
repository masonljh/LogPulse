package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antdev.logpulse.domain.model.LogEvent

@Composable
fun LogDetailPane(
    log: LogEvent?,
    flowTrace: com.antdev.logpulse.domain.model.FlowTrace? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2D2D2D))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (log == null) {
            Text(
                text = "로그를 선택하여 상세 정보를 확인하세요.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        } else {
            Text(
                text = "Log Details",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            DetailItem(label = "Timestamp", value = log.timestamp)
            DetailItem(label = "Level", value = log.level.name, valueColor = log.level.toColor())
            DetailItem(label = "Tag", value = log.tag, valueColor = log.level.toColor())
            DetailItem(label = "PID / TID", value = "${log.pid} / ${log.tid}")
            
            // Flow Trace Section
            if (flowTrace != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Flow Trace (${flowTrace.id})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252526))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        flowTrace.logs.forEach { matched ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = "◉",
                                    color = Color(0xFF007ACC),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = matched.pattern.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        
                        Text(
                            text = "Status: ${flowTrace.status::class.simpleName}",
                            color = when(flowTrace.status) {
                                is com.antdev.logpulse.domain.model.FlowStatus.Complete -> Color.Green
                                else -> Color.Yellow
                            },
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Message",
                color = Color.LightGray,
                fontSize = 12.sp
            )
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Text(
                    text = log.message,
                    color = log.level.toColor(),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Raw Data",
                color = Color.LightGray,
                fontSize = 12.sp
            )
            
            Text(
                text = log.rawData,
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$label: ",
            color = Color.LightGray,
            fontSize = 13.sp,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
