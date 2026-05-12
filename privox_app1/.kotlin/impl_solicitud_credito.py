from datetime import datetime
import pytz
from sentry_sdk import capture_exception
from sqlalchemy.orm import Session
from starlette import status

from project.application.dtos.consulta_cupo_dto import ConsultaCupoRequestDto, ConsultaCupoResponseDto
from project.application.dtos.solicitud_credito_dto import SolicitudCreditoDto
from project.application.dtos.consulta_sol_credito_dto import ConsultaSolCreditoRequestDto, ConsultaSolCreditoResponseDto
from project.application.dtos.aprobacion_credito_dto import AprobacionCreditoRequestDto, AprobacionCreditoResponseDto
from project.application.dtos.consulta_facturas_dto import ConsultaFacturasRequestDto, ConsultaFacturasResponseDto
from project.application.dtos.consulta_estado_cuenta_dto import ConsultaEstadoCuentaRequestDto, ConsultaEstadoCuentaResponseDto
from project.domains.repositories.credito_repositorios import SolicitudCreditoRepositorio
from project.utils.utils import ResponseStructure
from project.utils.mappers import solicitud_credito_dto_to_schema
from project.models.models_db import CarSolicitudesExternas, CarRubrosSolicitudExternas, CarReferenciaSolExternas, CarDireccionesSolExternas
from project.constans import cv_pendiente


class ImplSolicitudCreditoRepositorio(SolicitudCreditoRepositorio):

    def __init__(self, db: Session):
        self.db = db

    # =======================
    # WS-FPX-001 – Consulta Cupo
    # Servicio WEB: cupo_cliente (POST)
    # =======================
    def cupo_cliente(self, data: ConsultaCupoRequestDto) -> ResponseStructure:
        _r = ResponseStructure()
        try:
            # Dummy implementation
            response_dummy = ConsultaCupoResponseDto(
                codRespuesta="200",
                mensajeRespuesta="Consulta cupo OK",
                cupEstado="APROBADO",
                cupDisponible=1500.00,
            )

            _r.data = response_dummy.model_dump()
            _r.message = "OK"
            _r.status_code = status.HTTP_200_OK
            _r.error = None

        except Exception as e:
            capture_exception(e)
            _r.data = None
            _r.error = str(e)
            _r.message = "Error al procesar consulta de cupo"
            _r.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR

        return _r

    # =======================
    # WS-FPX-002 – Solicitud de crédito - Basado en Cotización
    # Servicio WEB: solicitud_credito (POST)
    # =======================
    def solicitud_credito(self, data: SolicitudCreditoDto) -> ResponseStructure:
        _r = ResponseStructure()
        db = self.db
        try:
            # Mapeo de SolicitudCreditoDto a SolicitudSchema (formato ecommerce)
            solicitud_schema = solicitud_credito_dto_to_schema(data)

            # Extraer datos de la persona
            datos = solicitud_schema["datos_persona"]

            # Crear la solicitud principal
            solicitud = CarSolicitudesExternas(
                sex_identificacion=datos["cedula"],
                sex_tipo_identificacion=datos["tipo_de_identificación"],
                sex_fecha_nacimiento=datetime.strptime(datos["fecha_nacimiento"], "%d-%m-%Y").astimezone(
                    pytz.timezone("America/Guayaquil")).date() if datos["fecha_nacimiento"] else None,
                sex_lugar_nacimiento=datos["lugar_nacimiento"],
                sex_nacionalidad=datos["nacionalidad"],
                sex_estado_civil=datos["estado_civil"],
                sex_tipo_vivienda=datos["tipo_vivienda"],
                sex_reside_desde=datetime.strptime(datos["reside_desde"], "%d-%m-%Y").astimezone(
                    pytz.timezone("America/Guayaquil")).date() if datos["reside_desde"] else None,
                sex_cargas_familiares=datos["cargas_familiares"],
                sex_correo=datos["correo"],
                fecha_ingreso=datetime.now(tz=pytz.timezone("America/Guayaquil")),
                sex_estado_solicitud=cv_pendiente,
                sex_tip_tel=datos["tip_tel"],
                sex_telefono=datos["telefono"],
                sex_fecha_ingreso_trabajo=datos["fechaIngresoTrabajo"],
                sex_cod_b2b=datos["codB2B"],
                sex_cod_tienda=datos["codTienda"],
                sex_scampo1=datos["sCampo1"],
                sex_scampo2=datos["sCampo2"],
                sex_scampo3=datos["sCampo3"]
            )

            db.add(solicitud)
            db.flush()

            # Agregar rubro si existe
            rubro = solicitud_schema["rubros"]
            if rubro:
                rubro_model = CarRubrosSolicitudExternas(
                    sex_id=solicitud.sex_id,
                    tru_id=rubro["id"]
                )
                db.add(rubro_model)

            # Agregar referencias
            for ref in solicitud_schema["referencias"]:
                referencia_model = CarReferenciaSolExternas(
                    sex_id=solicitud.sex_id,
                    rex_nombre=ref["nombre"],
                    tpa_id=ref["relacion"],
                    rex_telefono=ref["telefono"],
                    tip_tel=ref["tip_tel"],
                    rex_apellido=ref["apellido"],
                    rex_ingreso_financiero=None
                )
                db.add(referencia_model)

            # Agregar direcciones
            for dir_item in solicitud_schema["Direcciones"]:
                direccion_model = CarDireccionesSolExternas(
                    sex_id=solicitud.sex_id,
                    dse_direccion=dir_item["direcciones"],
                    dse_referencia=dir_item["referencia"],
                    ubi_id=dir_item["ubi_id"],
                    tdi_id=dir_item["tid_id"],
                    dse_coordenada_x=dir_item["x"],
                    dse_coordenada_y=dir_item["y"]
                )
                db.add(direccion_model)

            db.commit()

            _r.data = {
                "solicitud_id": solicitud.sex_id,
                "estado": "GENERADA",
                "mensaje": "Solicitud de crédito procesada correctamente"
            }
            _r.message = "Solicitud de credito generada correctamente"
            _r.status_code = status.HTTP_201_CREATED
            _r.error = None

        except Exception as e:
            capture_exception(e)
            try:
                db.rollback()
            except Exception:
                pass

            _r.data = None
            _r.error = str(e)
            _r.message = "Error al generar la solicitud de credito"
            _r.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR

        return _r

    # =======================
    # WS-FPX-003 – Consulta de Crédito
    # Servicio WEB: consulta_sol_credito (POST)
    # =======================
    def consulta_sol_credito(self, data: ConsultaSolCreditoRequestDto) -> ResponseStructure:
        _r = ResponseStructure()
        try:
            # Dummy implementation
            response_dummy = ConsultaSolCreditoResponseDto(
                codRespuesta="200",
                mensajeRespuesta="Consulta credito procesada correctamente",
                solEstado="GEN",
                solEstadoObservacion="Crédito generado con éxito",
                cupEstado="APROBADO",
                cupDisponible=2000.00,
            )

            _r.data = response_dummy.model_dump()
            _r.message = "OK"
            _r.status_code = status.HTTP_200_OK
            _r.error = None

        except Exception as e:
            capture_exception(e)
            _r.data = None
            _r.error = str(e)
            _r.message = "Error al procesar consulta de credito"
            _r.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR

        return _r

    # =======================
    # WS-FPX-004 – Aprobación de Crédito (Factura)
    # Servicio WEB: nuevo_credito (POST)
    # =======================
    def nuevo_credito(self, data: AprobacionCreditoRequestDto) -> ResponseStructure:
        _r = ResponseStructure()
        try:
            # Dummy implementation
            response_dummy = AprobacionCreditoResponseDto(
                codRespuesta="200",
                mensajeRespuesta="Aprobación procesada correctamente",
            )

            _r.data = response_dummy.model_dump()
            _r.message = "OK"
            _r.status_code = status.HTTP_200_OK
            _r.error = None

        except Exception as e:
            capture_exception(e)
            _r.data = None
            _r.error = str(e)
            _r.message = "Error al procesar aprobacion de credito"
            _r.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR

        return _r

    # =======================
    # WS-FPX-005 – Consulta de Facturas
    # Servicio WEB: documentos (POST)
    # =======================
    def documentos(self, data: ConsultaFacturasRequestDto) -> ResponseStructure:
        _r = ResponseStructure()
        try:
            # Dummy implementation
            response_dummy = ConsultaFacturasResponseDto(
                codRespuesta=200,
                nombreRespuesta="Factura generada correctamente",
                docCedula="base64-encoded-cedula-placeholder",
                docPagare="base64-encoded-pagare-placeholder",
                docTablaAmortizacion="base64-encoded-tabla-amortizacion-placeholder",
                docContratoDominio="base64-encoded-contrato-dominio-placeholder",
                docEncargoFiduciario="base64-encoded-encargo-fiduciario-placeholder",
                docSolCupo="base64-encoded-solicitud-cupo-placeholder",
                sCampo1="Información adicional 1",
                sCampo2="Información adicional 2",
                sCampo3="Información adicional 3",
            )

            _r.data = response_dummy.model_dump()
            _r.message = "OK"
            _r.status_code = status.HTTP_200_OK
            _r.error = None

        except Exception as e:
            capture_exception(e)
            _r.data = None
            _r.error = str(e)
            _r.message = "Error al consultar facturas"
            _r.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR

        return _r

    # =======================
    # WS-FPX-006 – Consultar el estado de cuenta del cliente
    # Servicio WEB: estado_cuenta (POST)
    # =======================
    def estado_cuenta(self, data: ConsultaEstadoCuentaRequestDto) -> ResponseStructure:
        _r = ResponseStructure()
        try:
            # Dummy implementation
            response_dummy = ConsultaEstadoCuentaResponseDto(
                estadoCartera="GENERADA",
                valorPendiente=500.00,
                saldoCredito=1200.00,
                fCampo1=0.0,
                fCampo2=0.0,
                sCampo1="Información adicional 1",
                sCampo2="Información adicional 2",
            )

            _r.data = response_dummy.model_dump()
            _r.message = "OK"
            _r.status_code = status.HTTP_200_OK
            _r.error = None

        except Exception as e:
            capture_exception(e)
            _r.data = None
            _r.error = str(e)
            _r.message = "Error al consultar estado de cuenta"
            _r.status_code = status.HTTP_500_INTERNAL_SERVER_ERROR

        return _r