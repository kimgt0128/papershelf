package io.github.kimgt0128.papershelf.logging;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class StageLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(StageLoggingAspect.class);

    @Around(value = "@annotation(stage)", argNames = "call,stage")
    public Object logStage(ProceedingJoinPoint call, LogStage stage) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = call.proceed();
            log.atInfo()
                    .addKeyValue("stage", stage.value())
                    .addKeyValue("elapsed_ms", elapsedMillis(start))
                    .log("stage.completed");
            return result;
        } catch (Throwable failure) {
            log.atWarn()
                    .addKeyValue("stage", stage.value())
                    .addKeyValue("elapsed_ms", elapsedMillis(start))
                    .addKeyValue("error_type", failure.getClass().getName())
                    .log("stage.failed");
            throw failure;
        }
    }

    private static long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
