package zio.metrics.connectors.micrometer.internal

import java.lang.{Double => JDouble}
import java.util.concurrent.atomic.AtomicLong

/**
 * Scala's `Double` implementation does not play nicely with Java's
 * `AtomicReference.compareAndSwap` as `compareAndSwap` uses Java's `==`
 * reference equality when it performs an equality check. This means that even
 * if two Scala `Double`s have the same value, they will still fail
 * `compareAndSwap` as they will most likely be two, distinct object
 * references. Thus, `compareAndSwap` will fail.
 *
 * This `AtomicDouble` implementation is a workaround for this issue that is
 * backed by an `AtomicLong` instead of an `AtomicReference` in which the
 * Double's bits are stored as a Long value. This approach also reduces boxing
 * and unboxing overhead that can be incurred with `AtomicReference`.
 */
final private[micrometer] class AtomicDouble private (private val ref: AtomicLong) extends AnyVal {

  def get(): Double =
    JDouble.longBitsToDouble(ref.get())

  def set(newValue: Double): Unit =
    ref.set(JDouble.doubleToLongBits(newValue))

  def compareAndSet(expected: Double, newValue: Double): Boolean =
    ref.compareAndSet(JDouble.doubleToLongBits(expected), JDouble.doubleToLongBits(newValue))

  def incrementBy(value: Double): Unit = {
    var loop = true

    while (loop) {
      val current = get()
      loop = !compareAndSet(current, current + value)
    }
  }
}
private[micrometer] object AtomicDouble {
  def make(value: Double): AtomicDouble =
    new AtomicDouble(new AtomicLong(JDouble.doubleToLongBits(value)))
}
