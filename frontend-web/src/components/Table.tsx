import { ReactNode } from 'react'

interface Props {
  columnas: string[]
  vacio?: string
  children: ReactNode
  hayFilas: boolean
}

/** Tabla de libro: cabecera sobre una regla gruesa de tinta, filas separadas por una línea fina y cifras tabulares. */
export default function Table({ columnas, vacio = 'Sin registros.', children, hayFilas }: Props) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-sm">
        <thead>
          <tr className="border-b-2 border-tinta">
            {columnas.map((c, i) => (
              <th key={`${c}-${i}`} scope="col" className="px-3 py-2 text-left font-semibold text-tinta">{c}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-tinta/10">
          {hayFilas ? children : (
            <tr><td colSpan={columnas.length} className="px-3 py-8 text-center text-slate-500">{vacio}</td></tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

export const Td = ({ children, className = '' }: { children?: ReactNode; className?: string }) => (
  <td className={`px-3 py-2.5 align-top text-slate-700 ${className}`}>{children}</td>
)
