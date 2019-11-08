package com.ericsson.clc.samples.ssemvc

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.dsl.SourcePollingChannelAdapterSpec
import org.springframework.integration.dsl.Transformers
import org.springframework.integration.file.dsl.Files
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

@SpringBootApplication
class SseMvcApplication

fun main(args: Array<String>) {
  runApplication<SseMvcApplication>(*args)
}

@Configuration
class IntegrationConfig {

  @Autowired
  lateinit var emitter: Emitter

  @ServiceActivator(inputChannel = "files")
  fun handleFile(path: String) {
    emitter.emit(path)
  }

  @Bean
  fun inboundFlow(@Value("\${input-dir:file://\${HOME}/Desktop/in}") `in`: File): IntegrationFlow {
		val channelAdapterSpec = Files
				.inboundAdapter(`in`)
				.autoCreateDirectory(true)

//    val handler = GenericHandler<String> { path, _ ->
//      sses.forEach { (_: String, sse: SseEmitter) ->
//        sse.send(path)
//      }
//    }
    val pollerSpec = Consumer<SourcePollingChannelAdapterSpec> { it.poller { spec -> spec.fixedDelay(500) } }
    return IntegrationFlows
				.from(channelAdapterSpec, pollerSpec)
        .transform(Transformers.converter<File, String> { it.absolutePath })
        .channel("files")
//        .handle(handler)
        .get()

  }
}

@RestController
class HomeController {
  @Autowired
  lateinit var emitter: Emitter

  @GetMapping("/files/{name}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun files(@PathVariable name: String): SseEmitter {
    val sseEmitter = SseEmitter(60 * 1000)
    emitter.put(name, sseEmitter)
    return sseEmitter
  }
}

@Service
class Emitter {
  private final val clients = ConcurrentHashMap<String, SseEmitter>()

  fun put(name: String, emitter: SseEmitter) {
    clients[name] = emitter
  }

  fun emit(value: String) {
    clients.forEach { (_: String, sse: SseEmitter) ->
      sse.send(value)
    }
  }

}