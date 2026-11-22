package cl.respirarysoltar.identidad.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de la API completa (controlador + servicio + JPA con H2): crear y luego leer por id.
 * Cubre la consulta que hace la función serverless fn-confirmacion-cita (GET /api/usuarios/{id}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void crearYLeerUnUsuarioPorId() throws Exception {
        MvcResult creado = mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Camila Rojas\",\"email\":\"camila.api@correo.cl\",\"rol\":\"PACIENTE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(creado.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/usuarios/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("camila.api@correo.cl"))
                .andExpect(jsonPath("$.contactosEmergencia").isArray());
    }
}
