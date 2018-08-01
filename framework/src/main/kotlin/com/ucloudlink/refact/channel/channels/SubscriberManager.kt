package com.ucloudlink.refact.channel.channels
import rx.Subscriber
import java.util.*

/**
 * Created by wangliang on 2016/9/29.
 */
class SubscriberManager<T> {
    private var subSet = HashMap<Int, Subscriber<T>>()

    fun putSub(sub: Subscriber<T>) {
        subSet.put(sub.hashCode(), sub)
    }

    fun delSubAndUnsubscribe(sub: Subscriber<T>) {
        subSet.remove(sub.hashCode())
        if (!sub.isUnsubscribed) sub.unsubscribe()
    }

    fun cleanSubsAndunSubscriberAll() {
        if (!subSet.isEmpty()) {
            subSet.forEach {
                if (!it.value.isUnsubscribed) it.value.unsubscribe()
            }
            subSet.clear()
        }
    }

    fun getSize(): Int {
        return subSet.size
    }
}