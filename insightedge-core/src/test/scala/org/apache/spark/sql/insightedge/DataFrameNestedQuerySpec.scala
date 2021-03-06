/*
 * Copyright (c) 2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.insightedge

import com.gigaspaces.document.{DocumentProperties, SpaceDocument}
import com.j_spaces.core.client.SQLQuery
import org.apache.spark.sql.insightedge.model.{Address, Person}
import org.insightedge.spark.fixture.{InsightEdge, IEConfig, Spark}
import org.insightedge.spark.implicits.all._
import org.insightedge.spark.utils.{JavaSpaceClass, ScalaSpaceClass}
import org.scalatest.FlatSpec

class DataFrameNestedQuerySpec extends FlatSpec with IEConfig with InsightEdge with Spark {

  it should "support nested properties" taggedAs ScalaSpaceClass in {
    sc.parallelize(Seq(
      new Person(id = null, name = "Paul", age = 30, address = new Address(city = "Columbus", state = "OH")),
      new Person(id = null, name = "Mike", age = 25, address = new Address(city = "Buffalo", state = "NY")),
      new Person(id = null, name = "John", age = 20, address = new Address(city = "Charlotte", state = "NC")),
      new Person(id = null, name = "Silvia", age = 27, address = new Address(city = "Charlotte", state = "NC"))
    )).saveToGrid()

    val df = sql.read.grid.loadClass[Person]
    df.printSchema()
    assert(df.count() == 4)
    assert(df.filter(df("address.city") equalTo "Buffalo").count() == 1)

    df.registerTempTable("people")

    val unwrapDf = sql.sql("select address.city as city, address.state as state from people")
    val countByCity = unwrapDf.groupBy("city").count().collect().map(row => row.getString(0) -> row.getLong(1)).toMap
    assert(countByCity("Charlotte") == 2)
    unwrapDf.printSchema()
  }

  it should "support nested properties [java]" taggedAs JavaSpaceClass in {
    parallelizeJavaSeq(sc, () => Seq(
      new JPerson(null, "Paul", 30, new JAddress("Columbus", "OH")),
      new JPerson(null, "Mike", 25, new JAddress("Buffalo", "NY")),
      new JPerson(null, "John", 20, new JAddress("Charlotte", "NC")),
      new JPerson(null, "Silvia", 27, new JAddress("Charlotte", "NC"))
    )).saveToGrid()

    val df = sql.read.grid.loadClass[JPerson]
    df.printSchema()
    assert(df.count() == 4)
    assert(df.filter(df("address.city") equalTo "Buffalo").count() == 1)

    df.registerTempTable("people")

    val unwrapDf = sql.sql("select address.city as city, address.state as state from people")
    val countByCity = unwrapDf.groupBy("city").count().collect().map(row => row.getString(0) -> row.getLong(1)).toMap
    assert(countByCity("Charlotte") == 2)
    unwrapDf.printSchema()
  }

  it should "support nested properties after saving" taggedAs ScalaSpaceClass in {
    sc.parallelize(Seq(
      new Person(id = null, name = "Paul", age = 30, address = new Address(city = "Columbus", state = "OH")),
      new Person(id = null, name = "Mike", age = 25, address = new Address(city = "Buffalo", state = "NY")),
      new Person(id = null, name = "John", age = 20, address = new Address(city = "Charlotte", state = "NC")),
      new Person(id = null, name = "Silvia", age = 27, address = new Address(city = "Charlotte", state = "NC"))
    )).saveToGrid()

    val collectionName = randomString()
    sql.read.grid.loadClass[Person].write.grid(collectionName).save()

    val person = spaceProxy.read(new SQLQuery[SpaceDocument](collectionName, ""))
    assert(person.getProperty[Any]("address").isInstanceOf[DocumentProperties])
    assert(person.getProperty[Any]("age").isInstanceOf[Integer])
    assert(person.getProperty[Any]("name").isInstanceOf[String])

    val df = sql.read.grid.load(collectionName)
    assert(df.count() == 4)
    assert(df.filter(df("address.state") equalTo "NC").count() == 2)
    assert(df.filter(df("address.city") equalTo "Nowhere").count() == 0)
  }

  it should "support nested properties after saving [java]" taggedAs ScalaSpaceClass in {
    parallelizeJavaSeq(sc, () => Seq(
      new JPerson(null, "Paul", 30, new JAddress("Columbus", "OH")),
      new JPerson(null, "Mike", 25, new JAddress("Buffalo", "NY")),
      new JPerson(null, "John", 20, new JAddress("Charlotte", "NC")),
      new JPerson(null, "Silvia", 27, new JAddress("Charlotte", "NC"))
    )).saveToGrid()

    val collectionName = randomString()
    sql.read.grid.loadClass[JPerson].write.grid(collectionName).save()

    val person = spaceProxy.read(new SQLQuery[SpaceDocument](collectionName, ""))
    assert(person.getProperty[Any]("address").isInstanceOf[DocumentProperties])
    assert(person.getProperty[Any]("age").isInstanceOf[Integer])
    assert(person.getProperty[Any]("name").isInstanceOf[String])

    val df = sql.read.grid.load(collectionName)
    assert(df.count() == 4)
    assert(df.filter(df("address.state") equalTo "NC").count() == 2)
    assert(df.filter(df("address.city") equalTo "Nowhere").count() == 0)
  }


}
