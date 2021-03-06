package com.vacalendar.validation

import java.time.Clock
import cats.data._

import com.vacalendar.errors.ServiceValidationError
import com.vacalendar.domain._

trait ServiceValidationAlgebra {

  def validateEmplIn(emplIn: EmployeeIn, 
                     foundPos: Option[Position]): Either[NonEmptyList[ServiceValidationError], EmployeeIn]

  def checkVacIsChangeable(vac: Vacation, clock: Clock = Clock.systemUTC()): Either[ServiceValidationError, Vacation]

  def basicValidateVacIn(vacIn: VacationIn, clock: Clock = Clock.systemUTC()): Either[NonEmptyList[ServiceValidationError], VacationIn]

  def validateVacInCreate(vacIn: VacationIn,
                          emplId: Long,
                          emplVacsCurrYear: List[Vacation],
                          overlappedVacsWithSamePosId: List[Vacation],
                          emplsWithSamePosId: List[Employee]): Either[NonEmptyList[ServiceValidationError], VacationIn]

  def validateVacInUpdate(vacIn: VacationIn,
                          emplId: Long,
                          vacId: Long,
                          emplVacsCurrYear: List[Vacation],
                          overlappedVacsWithSamePosId: List[Vacation],
                          emplsWithSamePosId: List[Employee]): Either[NonEmptyList[ServiceValidationError], VacationIn]

}
