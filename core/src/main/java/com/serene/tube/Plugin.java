package com.serene.tube;

import com.codahale.metrics.MetricRegistry;

public interface Plugin {
    MetricRegistry metricRegistry = new MetricRegistry();

    /**
     * 在{@link Plugin#hurryOver()}之后调用，告知插件系统即将关闭，并且在调用该方法之后，
     * 不会在调用插件的其他方法，插件要做最后的清理工作。
     */
    default void shutdown() {
    }

    /**
     * 在{@link Plugin#shutdown()}之前调用，通知该插件输出队列{@link OutputQueue}正在做最后的清理，
     * 当输出队列清理完毕时，会调用{@link Plugin#shutdown()}；在该方法调用之后，可能会调用插件的其他方法。
     */
    default void hurryOver() {
    }
}
