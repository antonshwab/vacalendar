package com.vacalendar.service

import java.time._
import cats.data._
import cats.implicits._
import cats.effect.Effect

import com.vacalendar.errors._
import com.vacalendar.domain._
import com.vacalendar.endpoints.QryParams.VacsQryParams
import com.vacalendar.validation.ServiceValidationAlgebra
import com.vacalendar.repository.{ VacationRepoAlgebra, EmployeeRepoAlgebra }

class VacationService[F[_]: Effect](vacRepo: VacationRepoAlgebra[F],
                                    emplRepo: EmployeeRepoAlgebra[F],
                                    V: ServiceValidationAlgebra) {

  def getVac(emplId: Long, vacId: Long): EitherT[F, AppError, Option[Vacation]] =
    for {
      optVac <- vacRepo.getVac(emplId, vacId)
    } yield optVac

  def getVacs(emplId: Long, qryParams: VacsQryParams): EitherT[F, AppError, List[Vacation]] =
    for {
      optFoundEmpl <- emplRepo.getEmpl(emplId)

      _ <- EitherT
        .fromOption[F](optFoundEmpl, EmplNotFound)
        .leftMap[AppError](AppError.ServiceValidationErrWrapper)

      vacs <- vacRepo.getVacs(emplId, qryParams)

    } yield vacs

  def deleteVac(emplId: Long, vacId: Long, clock: Clock): EitherT[F, AppError, Option[Vacation]] =
    for {
      optVac <- vacRepo.getVac(emplId, vacId)
      vac <- EitherT
        .fromOption[F](optVac, VacNotFound)
        .leftMap[AppError](AppError.ServiceValidationErrWrapper)

      _ <- EitherT.fromEither[F] {
        V.checkVacIsChangeable(vac, clock)
          .leftMap[AppError](AppError.ServiceValidationErrWrapper)
      }

      optDeletedVac <- vacRepo.deleteVac(emplId, vacId)
    } yield optDeletedVac

  def createVac(emplId: Long, vacIn: VacationIn, clock: Clock): EitherT[F, AppError, Vacation] = {
    for {
      optFoundEmpl <- emplRepo.getEmpl(emplId)

      empl <- EitherT
        .fromOption[F](optFoundEmpl, EmplNotFound)
        .leftMap[AppError](AppError.ServiceValidationErrWrapper)

      validVacIn1 <- EitherT.fromEither[F] {
        V.basicValidateVacIn(vacIn, clock)
          .leftMap[AppError](AppError.ServiceValidationErrsWrapper)
      }

      emplVacsCurrYear <- vacRepo.getEmplVacsCurrYear(emplId, clock)

      overlappedVacsWithSamePosId <- vacRepo.getOverlappedPosIdVacs(empl.positionId, vacIn.since, vacIn.until, clock)

      emplsWithSamePosId <- emplRepo.getEmplsByPosId(empl.positionId)

      validVacIn2 <- EitherT.fromEither[F] {
        V.validateVacInCreate(validVacIn1,
                              emplId,
                              emplVacsCurrYear,
                              overlappedVacsWithSamePosId,
                              emplsWithSamePosId)
        .leftMap[AppError](AppError.ServiceValidationErrsWrapper)
      }

      createdVac <- vacRepo.createVac(emplId, validVacIn2, clock)

    } yield createdVac
  }

  def updateVac(emplId: Long, vacId: Long, vacIn: VacationIn, clock: Clock): EitherT[F, AppError, Vacation] = {
    for {

      optFoundEmpl <- emplRepo.getEmpl(emplId)
      empl <- EitherT
        .fromOption[F](optFoundEmpl, EmplNotFound)
        .leftMap[AppError](AppError.ServiceValidationErrWrapper)

      optVac <- vacRepo.getVac(emplId, vacId)
      vac <- EitherT
        .fromOption[F](optVac, VacNotFound)
        .leftMap[AppError](AppError.ServiceValidationErrWrapper)

      _ <- EitherT.fromEither[F] {
        V.checkVacIsChangeable(vac, clock)
        .leftMap[AppError](AppError.ServiceValidationErrWrapper)
      }

      validVacIn1 <- EitherT.fromEither[F] {
        V.basicValidateVacIn(vacIn, clock)
        .leftMap[AppError](AppError.ServiceValidationErrsWrapper)
      }

      emplVacsCurrYear <- vacRepo.getEmplVacsCurrYear(empl.employeeId, clock)

      overlappedVacsWithSamePosId <- vacRepo
        .getOverlappedPosIdVacs(empl.positionId, vacIn.since, vacIn.until, clock)

      emplsWithSamePosId <- emplRepo.getEmplsByPosId(empl.positionId)

      validVacIn2 <- EitherT.fromEither[F] {
        V.validateVacInUpdate(validVacIn1,
                              emplId,
                              vacId,
                              emplVacsCurrYear,
                              overlappedVacsWithSamePosId,
                              emplsWithSamePosId)
        .leftMap[AppError](AppError.ServiceValidationErrsWrapper)
      }

      optUpdatedVac <- vacRepo.updateVac(emplId, vacId, validVacIn2, clock)

      updatedVac <- EitherT
        .fromOption[F](optUpdatedVac, VacNotFound)
        .leftMap[AppError](AppError.ServiceValidationErrWrapper)

    } yield updatedVac
  }
}