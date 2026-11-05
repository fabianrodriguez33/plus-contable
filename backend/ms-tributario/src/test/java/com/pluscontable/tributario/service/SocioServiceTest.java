package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.ResumenSocietarioDto;
import com.pluscontable.tributario.dto.SocioEntradaDto;
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

/** HU-02: el porcentaje de cada socio se calcula según su aporte; capital social total y verificación del 100 %. */
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

    private static SocioEntradaDto socio(String nombre, String aporte) {
        return new SocioEntradaDto(nombre, "DNI", nombre.hashCode() + "", new BigDecimal(aporte), null);
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
    void elPorcentajeSeCalculaSegunElAporteYSumaExactamente100() {
        guardarDevuelveLoRecibido();

        ResumenSocietarioDto r = service.reemplazar(CLIENTE,
                List.of(socio("Ana", "60000.00"), socio("Luis", "40000.50")));

        assertThat(r.completo()).isTrue();
        assertThat(r.capitalSocialTotal()).isEqualByComparingTo("100000.50");
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
        assertThat(r.diferencia()).isEqualByComparingTo("0");
        assertThat(r.sociosSinAporte()).isZero();
        assertThat(r.socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("60.00", "40.00");
        verify(socios).deleteByClienteId(CLIENTE);
    }

    @Test
    void elResiduoDeTresAportesIgualesVaAlPrimeroPorOrdenDeAlta() {
        guardarDevuelveLoRecibido();

        ResumenSocietarioDto r = service.reemplazar(CLIENTE, List.of(
                socio("A", "1"), socio("B", "1"), socio("C", "1")));

        assertThat(r.socios()).extracting(s -> s.porcentaje().toPlainString()).containsExactly("33.34", "33.33", "33.33");
        assertThat(r.completo()).isTrue();
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void unAporteEnCeroSeRechazaConUnMensajeClaroYNoSeGuardaNada() {
        assertThatThrownBy(() -> service.reemplazar(CLIENTE, List.of(socio("Ana", "1000"), socio("Luis", "0"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("El aporte de Luis debe ser mayor a 0");
        verify(socios, never()).saveAll(any());
        verify(socios, never()).deleteByClienteId(any());
    }

    @Test
    void unAporteTanPequenoQueSuParticipacionSeriaCeroSeRechaza() {
        assertThatThrownBy(() -> service.reemplazar(CLIENTE, List.of(socio("Ana", "10000000"), socio("Luis", "0.01"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("menor a 0.01%");
        verify(socios, never()).saveAll(any());
    }

    @Test
    void sinSociosNoSeGuarda() {
        assertThatThrownBy(() -> service.reemplazar(CLIENTE, List.of())).isInstanceOf(ValidationException.class);
        verify(socios, never()).saveAll(any());
    }

    @Test
    void editarElAporteRecalculaElPorcentajeDeTodosLosSociosYElCapital() {
        Socio ana = entidad(1, "60000.00", "60.00");
        Socio luis = entidad(2, "40000.00", "40.00");
        when(socios.findByIdAndClienteId(1L, CLIENTE)).thenReturn(Optional.of(ana));
        when(socios.findByClienteIdOrderById(CLIENTE)).thenReturn(List.of(ana, luis));

        ResumenSocietarioDto r = service.actualizarAporte(CLIENTE, 1L, new BigDecimal("75000"));

        assertThat(ana.getAporte()).isEqualByComparingTo("75000.00");
        assertThat(ana.getPorcentaje()).isEqualByComparingTo("65.22");
        assertThat(luis.getPorcentaje()).isEqualByComparingTo("34.78");
        assertThat(r.capitalSocialTotal()).isEqualByComparingTo("115000.00");
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
        assertThat(r.completo()).isTrue();
        verify(socios).saveAll(List.of(ana, luis));
    }

    @Test
    void editarElAporteACeroSeRechazaYNoCambiaNada() {
        Socio ana = entidad(1, "60000.00", "60.00");
        when(socios.findByIdAndClienteId(1L, CLIENTE)).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.actualizarAporte(CLIENTE, 1L, BigDecimal.ZERO))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("debe ser mayor a 0");
        verify(socios, never()).save(any());
        verify(socios, never()).saveAll(any());
    }

    @Test
    void siOtroSocioAnteriorTieneAporteCeroSoloSeGuardaElAporteYQuedaIncompleto() {
        Socio ana = entidad(1, "0", "60.00");
        Socio luis = entidad(2, "0", "40.00");
        when(socios.findByIdAndClienteId(1L, CLIENTE)).thenReturn(Optional.of(ana));
        when(socios.findByClienteIdOrderById(CLIENTE)).thenReturn(List.of(ana, luis));

        ResumenSocietarioDto r = service.actualizarAporte(CLIENTE, 1L, new BigDecimal("5000"));

        assertThat(ana.getAporte()).isEqualByComparingTo("5000.00");
        assertThat(ana.getPorcentaje()).isEqualByComparingTo("60.00");
        assertThat(luis.getPorcentaje()).isEqualByComparingTo("40.00");
        assertThat(r.sociosSinAporte()).isEqualTo(1);
        assertThat(r.completo()).isFalse();
        verify(socios).save(ana);
        verify(socios, never()).saveAll(any());
    }

    @Test
    void editarElAporteDeUnSocioAjenoOInexistenteDa404() {
        when(socios.findByIdAndClienteId(99L, CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarAporte(CLIENTE, 99L, BigDecimal.TEN))
                .isInstanceOf(EntityNotFoundException.class);
        verify(socios, never()).save(any());
    }

    @Test
    void losSociosAnterioresConAporteCeroQuedanMarcadosComoIncompletosAunqueSuPorcentajeSume100() {
        when(socios.findByClienteIdOrderById(CLIENTE)).thenReturn(List.of(entidad(1, "0", "70"), entidad(2, "0", "30")));

        ResumenSocietarioDto r = service.resumen(CLIENTE);

        assertThat(r.capitalSocialTotal()).isEqualByComparingTo("0");
        assertThat(r.porcentajeTotal()).isEqualByComparingTo("100.00");
        assertThat(r.sociosSinAporte()).isEqualTo(2);
        assertThat(r.completo()).isFalse();
    }

    @Test
    void elResumenSumaAportesYPorcentajesGuardados() {
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

        assertThatThrownBy(() -> service.reemplazar(99L, List.of(socio("Ana", "1"))))
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

        service.reemplazar(CLIENTE, List.of(socio("Ana", "1")));

        verify(clientes).buscar(CLIENTE);
    }
}
