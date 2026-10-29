package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.ResumenSocietarioDto;
import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Socio;
import com.pluscontable.tributario.repository.SocioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** HU-02: aporte y porcentaje de cada socio, capital social total y diferencia respecto al 100 %. */
@ExtendWith(MockitoExtension.class)
class SocioServiceTest {

    private static final long CLIENTE = 7L;

    @Mock SocioRepository socios;
    @Mock ClienteService clientes;
    SocioService service;

    @BeforeEach
    void setUp() {
        service = new SocioService(socios, clientes);
    }

    private static SocioDto socio(String nombre, String aporte, String porcentaje) {
        return new SocioDto(null, nombre, "DNI", nombre.hashCode() + "", new BigDecimal(aporte), new BigDecimal(porcentaje), null);
    }

    private static Socio entidad(long id, String aporte, String porcentaje) {
        Socio s = new Socio();
        s.setClienteId(CLIENTE);
        s.setNombreCompleto("Socio " + id);
        s.setNumeroDocumento("D" + id);
        s.setAporte(new BigDecimal(aporte));
        s.setPorcentaje(new BigDecimal(porcentaje));
        try { // el id lo asigna la base; aqui se fija por reflexion para el test
            var f = Socio.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return s;
    }

    private void guardarDevuelveLoRecibido() {
        when(socios.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sumaExactaDe100SeGuardaYMuestraElCapitalSocialTotal() {
        guardarDevuelveLoRecibido();

        ResumenSocietarioDto r = service.reemplazar(CLIENTE,
                List.of(socio("Ana", "60000.00", "60"), socio("Luis", "40000.50", "40")));

        assertThat(r.completo()).isTrue();
        assertThat(r.capitalSocialTotal()).isEqualByComparingTo("100000.50");
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
        assertThat(r.diferencia()).isEqualByComparingTo("0");
        assertThat(r.socios()).hasSize(2);
        verify(socios).deleteByClienteId(CLIENTE);
    }

    @Test
    void sumaMenorDe100SeBloqueaEIndicaCuantoFalta() {
        assertThatThrownBy(() -> service.reemplazar(CLIENTE, List.of(socio("Ana", "1000", "60"), socio("Luis", "1000", "30"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("actual 90.00%")
                .hasMessageContaining("faltan 10.00%");
        verify(socios, never()).saveAll(any());
        verify(socios, never()).deleteByClienteId(any());
    }

    @Test
    void sumaMayorDe100SeBloqueaEIndicaCuantoSobra() {
        assertThatThrownBy(() -> service.reemplazar(CLIENTE, List.of(socio("Ana", "1000", "70"), socio("Luis", "1000", "45.5"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("actual 115.50%")
                .hasMessageContaining("sobran 15.50%");
        verify(socios, never()).saveAll(any());
    }

    @Test
    void sinSociosNoSeGuarda() {
        assertThatThrownBy(() -> service.reemplazar(CLIENTE, List.of())).isInstanceOf(ValidationException.class);
        verify(socios, never()).saveAll(any());
    }

    @Test
    void lasFraccionesQueSumanExactamente100SeAceptan() {
        guardarDevuelveLoRecibido();

        ResumenSocietarioDto r = service.reemplazar(CLIENTE, List.of(
                socio("A", "1", "33.33"), socio("B", "1", "33.33"), socio("C", "1", "33.34")));

        assertThat(r.completo()).isTrue();
    }

    @Test
    void editarElAporteActualizaElSocioYRecalculaElCapital() {
        Socio ana = entidad(1, "60000.00", "60.00");
        Socio luis = entidad(2, "40000.00", "40.00");
        when(socios.findByIdAndClienteId(1L, CLIENTE)).thenReturn(Optional.of(ana));
        when(socios.findByClienteIdOrderById(CLIENTE)).thenReturn(List.of(ana, luis));

        ResumenSocietarioDto r = service.actualizarAporte(CLIENTE, 1L, new BigDecimal("75000"));

        assertThat(ana.getAporte()).isEqualByComparingTo("75000.00");
        assertThat(r.capitalSocialTotal()).isEqualByComparingTo("115000.00");
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
        verify(socios).save(ana);
    }

    @Test
    void editarElAporteDeUnSocioAjenoOInexistenteDa404() {
        when(socios.findByIdAndClienteId(99L, CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarAporte(CLIENTE, 99L, BigDecimal.TEN))
                .isInstanceOf(EntityNotFoundException.class);
        verify(socios, never()).save(any());
    }

    @Test
    void elResumenSumaAportesYPorcentajesYMarcaIncompleto() {
        when(socios.findByClienteIdOrderById(CLIENTE)).thenReturn(List.of(entidad(1, "5000", "70"), entidad(2, "2500.25", "20")));

        ResumenSocietarioDto r = service.resumen(CLIENTE);

        assertThat(r.capitalSocialTotal()).isEqualByComparingTo("7500.25");
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("90.00");
        assertThat(r.diferencia()).isEqualByComparingTo("10.00");
        assertThat(r.completo()).isFalse();
    }

    @Test
    void losSociosSoloSeRegistranSobreUnClienteExistente() {
        when(clientes.buscar(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 99"));

        assertThatThrownBy(() -> service.reemplazar(99L, List.of(socio("Ana", "1", "100"))))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.actualizarAporte(99L, 1L, BigDecimal.ONE))
                .isInstanceOf(EntityNotFoundException.class);
        verify(socios, never()).saveAll(any());
        verify(socios, never()).save(any());
    }

    @Test
    void elClienteSeConsultaAntesDeGuardar() {
        guardarDevuelveLoRecibido();
        when(clientes.buscar(CLIENTE)).thenReturn(new Cliente());

        service.reemplazar(CLIENTE, List.of(socio("Ana", "1", "100")));

        verify(clientes).buscar(CLIENTE);
    }
}
