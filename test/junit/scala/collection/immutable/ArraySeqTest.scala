package scala.collection.immutable

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.reflect.ClassTag

@RunWith(classOf[JUnit4])
class ArraySeqTest {
  @Test
  def slice(): Unit = {

    implicit def array2ArraySeq[T](array: Array[T]): ArraySeq[T] =
      ArraySeq.unsafeWrapArray(array)

    val booleanArray = Array(true, false, true, false)
    check(booleanArray, Array(true, false), Array(false, true))

    val shortArray = Array(1.toShort, 2.toShort, 3.toShort, 4.toShort)
    check(shortArray, Array(1.toShort, 2.toShort), Array(2.toShort, 3.toShort))

    val intArray = Array(1, 2, 3, 4)
    check(intArray, Array(1, 2), Array(2, 3))

    val longArray = Array(1L, 2L, 3L, 4L)
    check(longArray, Array(1L, 2L), Array(2L, 3L))

    val byteArray = Array(1.toByte, 2.toByte, 3.toByte, 4.toByte)
    check(byteArray, Array(1.toByte, 2.toByte), Array(2.toByte, 3.toByte))

    val charArray = Array('1', '2', '3', '4')
    check(charArray, Array('1', '2'), Array('2', '3'))

    val doubleArray = Array(1.0, 2.0, 3.0, 4.0)
    check(doubleArray, Array(1.0, 2.0), Array(2.0, 3.0))

    val floatArray = Array(1.0f, 2.0f, 3.0f, 4.0f)
    check(floatArray, Array(1.0f, 2.0f), Array(2.0f, 3.0f))

    val refArray = Array("1", "2", "3", "4")
    check[String](refArray, Array("1", "2"), Array("2", "3"))

    def unit1(): Unit = {}
    def unit2(): Unit = {}
    assertEquals(unit1(), unit2())
    // unitArray is actually an instance of Immutable[BoxedUnit], the check to which is actually checked slice
    // implementation of ofRef
    val unitArray: ArraySeq[Unit] = Array(unit1(), unit2(), unit1(), unit2())
    check(unitArray, Array(unit1(), unit1()), Array(unit1(), unit1()))
  }

  @Test
  def t10851(): Unit = {
    val s1 = ArraySeq.untagged(1,2,3)
    assertTrue(s1.unsafeArray.getClass == classOf[Array[AnyRef]])
  }

  @Test
  def safeToArray(): Unit = {
    val a = ArraySeq(1,2,3)
    a.toArray.update(0, 100)
    assertEquals(a, List(1,2,3))
  }

  private def check[T : ClassTag](array: ArraySeq[T], expectedSliceResult1: ArraySeq[T], expectedSliceResult2: ArraySeq[T]) {
    assertEquals(array, array.slice(-1, 4))
    assertEquals(array, array.slice(0, 5))
    assertEquals(array, array.slice(-1, 5))
    assertEquals(expectedSliceResult1, array.slice(0, 2))
    assertEquals(expectedSliceResult2, array.slice(1, 3))
    assertEquals(ArraySeq.empty[T], array.slice(1, 1))
    assertEquals(ArraySeq.empty[T], array.slice(2, 1))
  }
  
  @Test def checkSearch: Unit = SeqTests.checkSearch(ArraySeq(0 to 1000: _*), 15,  implicitly[Ordering[Int]])
}