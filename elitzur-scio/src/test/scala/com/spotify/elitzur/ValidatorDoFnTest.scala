/*
 * Copyright 2020 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.spotify.elitzur

import java.util.Locale

import com.spotify.elitzur.example._
import com.spotify.elitzur.validators._
import com.spotify.elitzur.scio._
import com.spotify.scio.testing.PipelineSpec
import org.scalacheck.{Arbitrary, Gen}

import scala.collection.compat.immutable.ArraySeq

object TestClasses {
  case class Test(inner: Inner, countryCode: CountryCodeTesting)
  case class Inner(playCount: NonNegativeLongTesting)
  case class DynamicRecord(i: DynamicString, j: NonNegativeLongTesting)
}

class ValidatorDoFnTest extends PipelineSpec {

  "Validator SCollection helper" should "validate valid records" in {
    val validRecord = TestClasses.Test(TestClasses.Inner(NonNegativeLongTesting(0)),
      CountryCodeTesting("US"))

    runWithData(Seq(validRecord))(sc => {
      sc.validate()
        .count
    }) shouldBe Seq(1)
  }

  "Validator SCollection helper" should "validateWithResult autogenerated valid records" in {
    val validRecordGen = for {
      nnl <- Gen.posNum[Long]
      cc <- Gen.oneOf(ArraySeq.unsafeWrapArray(Locale.getISOCountries))
    } yield TestClasses.Test(TestClasses.Inner(NonNegativeLongTesting(nnl)), CountryCodeTesting(cc))
    val numberToValidate = 100
    val validRecords = Gen.listOfN(numberToValidate, validRecordGen).sample.get

    runWithData(validRecords)(sc =>
      sc.validateWithResult().filter(_.isValid).count) shouldBe Seq(numberToValidate)
  }

  "Validator SCollection helper" should "validateWithResult autogenerated invalid records" in {
    val invalidRecordGen = for {
      nnl <- Gen.negNum[Long]
      cc <- Gen.numStr
    } yield TestClasses.Test(TestClasses.Inner(NonNegativeLongTesting(nnl)), CountryCodeTesting(cc))
    val numberToValidate = 100
    val invalidRecords = Gen.listOfN(numberToValidate, invalidRecordGen).sample.get

    runWithData(invalidRecords)(sc =>
      sc.validateWithResult().filter(_.isInvalid).count) shouldBe Seq(numberToValidate)
  }

  "Validator SCollection" should "validate dynamic records" in {
    val dynamicGen: Gen[TestClasses.DynamicRecord] = for {
      s <- Arbitrary.arbString.arbitrary
      l <- Gen.posNum[Long]
    } yield TestClasses.DynamicRecord(DynamicString(s, Set(s)), NonNegativeLongTesting(l))

    val input = List(dynamicGen.sample.get)
    runWithData(input)(sc => {
      sc.validateWithResult().flatten
        .count
    }) shouldBe Seq(1)
  }
}
