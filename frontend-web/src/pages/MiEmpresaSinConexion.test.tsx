// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { AxiosError, type AxiosResponse } from 'axios'
import MiEmpresaPage from './MiEmpresaPage'
import { clienteIdActual, logout } from '../services/authService'
import { obtenerDatosEmpresa, type DatosEmpresa } from '../services/datosEmpresaService'

// Se prueba con la copia local real: solo se simula el servidor y el id de la cuenta.
vi.mock('../services/authService', async (original) => ({ ...(await original<object>()), clienteIdActual: vi.fn() }))
vi.mock('../services/datosEmpresaService', () => ({ obtenerDatosEmpresa: vi.fn() }))

const empresa = (razonSocial: string): DatosEmpresa => ({
  clienteId: 4, ruc: '20123456789', razonSocial, regimenTributario: 'RER', direccion: 'Av. Lima 123', telefono: null, email: null,
  socios: [], documentos: [{ tipo: 'MINUTA', id: null, version: null, fechaEmision: null }],
})
const sinInternet = () => new AxiosError('Network Error', 'ERR_NETWORK')
const rechazo = (status: number, mensaje: string) =>
  new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined, { status, data: { mensaje } } as AxiosResponse)

afterEach(() => { cleanup(); localStorage.clear(); vi.clearAllMocks(); vi.useRealTimers() })

describe('Consulta sin conexión (HU-34)', () => {
  it('criterio 1: sin internet muestra la última información con la fecha de actualización', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValueOnce(empresa('Acme SAC'))
    const primera = render(<MiEmpresaPage />)
    await screen.findByText('Acme SAC')
    primera.unmount()

    vi.mocked(obtenerDatosEmpresa).mockRejectedValue(sinInternet())
    render(<MiEmpresaPage />)

    await screen.findByText(/Sin conexión\. Se muestra la información de su última actualización: \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}\./)
    expect(screen.getByText('Acme SAC')).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('criterio 2: si nunca sincronizó, indica que debe conectarse la primera vez y no muestra datos', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockRejectedValue(sinInternet())
    render(<MiEmpresaPage />)

    await screen.findByText('Sin conexión. Conéctese a internet la primera vez para ver su información.')
    expect(screen.queryByText('Razón social')).toBeNull()
  })

  it('criterio 3: al volver el internet la información se actualiza y desaparece el aviso', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValueOnce(empresa('Acme SAC'))
    const primera = render(<MiEmpresaPage />)
    await screen.findByText('Acme SAC')
    primera.unmount()
    vi.mocked(obtenerDatosEmpresa).mockRejectedValueOnce(sinInternet())
    render(<MiEmpresaPage />)
    await screen.findByText(/Sin conexión\. Se muestra/)

    vi.mocked(obtenerDatosEmpresa).mockResolvedValue(empresa('Acme Nueva SAC'))
    act(() => { window.dispatchEvent(new Event('online')) })

    await screen.findByText('Acme Nueva SAC')
    expect(screen.queryByText(/Sin conexión/)).toBeNull()
    // la copia local quedó con lo nuevo: la próxima vez sin internet se verá esto
    vi.mocked(obtenerDatosEmpresa).mockRejectedValue(sinInternet())
    cleanup()
    render(<MiEmpresaPage />)
    await screen.findByText('Acme Nueva SAC')
  })

  it('al perder el internet con la página abierta avisa sin quitar lo que ya se ve', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValue(empresa('Acme SAC'))
    render(<MiEmpresaPage />)
    await screen.findByText('Acme SAC')

    act(() => { window.dispatchEvent(new Event('offline')) })

    await screen.findByText(/Sin conexión\. Se muestra la información de su última actualización/)
    expect(screen.getByText('Acme SAC')).toBeTruthy()
  })

  it('permisos: un rechazo del servidor (403) nunca se tapa con la copia local', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValueOnce(empresa('Acme SAC'))
    const primera = render(<MiEmpresaPage />)
    await screen.findByText('Acme SAC')
    primera.unmount()

    vi.mocked(obtenerDatosEmpresa).mockRejectedValue(rechazo(403, 'Un cliente solo accede a los datos de su empresa'))
    render(<MiEmpresaPage />)

    expect((await screen.findByRole('alert')).textContent).toBe('Un cliente solo accede a los datos de su empresa')
    expect(screen.queryByText('Acme SAC')).toBeNull()
  })

  it('permisos: la copia es de la empresa de la cuenta y se borra al cerrar sesión', async () => {
    vi.mocked(clienteIdActual).mockReturnValue(4)
    vi.mocked(obtenerDatosEmpresa).mockResolvedValueOnce(empresa('Acme SAC'))
    const primera = render(<MiEmpresaPage />)
    await screen.findByText('Acme SAC')
    primera.unmount()

    // otra cuenta (empresa 5) en el mismo equipo, sin internet: no ve la copia de la 4
    vi.mocked(clienteIdActual).mockReturnValue(5)
    vi.mocked(obtenerDatosEmpresa).mockRejectedValue(sinInternet())
    const otra = render(<MiEmpresaPage />)
    await screen.findByText(/Conéctese a internet la primera vez/)
    otra.unmount()

    // y al cerrar sesión la copia de la 4 ya no está
    logout()
    vi.mocked(clienteIdActual).mockReturnValue(4)
    render(<MiEmpresaPage />)
    await waitFor(() => expect(screen.getByText(/Conéctese a internet la primera vez/)).toBeTruthy())
    expect(screen.queryByText('Acme SAC')).toBeNull()
  })
})
