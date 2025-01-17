package com.backend.backend.controller;

import com.backend.backend.model.dto.MovieCsv;
import com.backend.backend.model.entities.Movie;
import com.backend.backend.service.MovieService;
import com.backend.backend.utils.QueryParamParser;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@Path("/v1/movie")
@RequestScoped
@Produces("application/json")
@Consumes("application/json")
public class MovieControllerV1 {

    @Inject
    private MovieService service;

    @POST
    public Response createMovie(@Valid Movie movie) {
        Logger logger = Logger.getLogger(getClass().getName()); // Создаем логгер для вывода информации в консоль или файл логов
        try {
            // Попытка сохранить объект movie
            service.save(movie);
            return Response.status(Response.Status.CREATED).entity(movie).build();
        } catch (ConstraintViolationException e) {
            // Логируем детали ошибки валидации
            StringBuilder errorMessage = new StringBuilder("The data is not completely filled in. Please check the entered data:\n");
            for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
                // Получаем имя поля и сообщение об ошибке
                String fieldName = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                errorMessage.append("Field: ").append(fieldName)
                        .append(" - ").append(message)
                        .append("\n");
            }
            logger.severe("Validation error: " + errorMessage.toString());  // Логирование ошибки валидации

            // Возвращаем ошибку с детализированным сообщением
            return Response.status(Response.Status.BAD_REQUEST).entity(errorMessage.toString()).build();
        } catch (Exception e) {
            // Логируем и печатаем другие исключения
            logger.severe("Unexpected error: " + e.getMessage());
            e.printStackTrace(); // Печатаем стек ошибки в консоль для диагностики
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("An unexpected error occurred.").build();
        }
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importMovie(@MultipartForm MultipartFormDataInput input) {
        if (input == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("File input stream is null")
                    .build();
        }

        Map<String, List<InputPart>> formData = input.getFormDataMap();
        List<InputPart> inputParts = formData.get("file");

        if (inputParts == null || inputParts.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("No file uploaded")
                    .build();
        }

        try {
             return service.importMoviesStream(inputParts.get(0).getBody(InputStream.class, null));
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Error processing the CSV file: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Internal server error: " + e.getMessage())
                    .build();
        }
    }

//    @GET
//    @Path("/import/history/{fileIndex}")
//    @Produces("text/csv")
//    public Response importFromHistory(@PathParam("fileIndex") UUID fileIndex) {
//        return service.importMoviesFromHistory(fileIndex);
//    }

    @GET
    @Path("/import/history/{fileIndex}")
    @Produces("application/octet-stream")
    public Response importFromHistory(@PathParam("fileIndex") UUID fileIndex) {
        return service.importMoviesFromHistory(fileIndex);
    }

//    @POST
//    @Path("/import")
//    @Consumes(MediaType.MULTIPART_FORM_DATA)
//    public Response importMovie(@MultipartForm MultipartFormDataInput input) {
//        if (input == null) {
//            return Response.status(Response.Status.BAD_REQUEST)
//                    .entity("File input stream is null")
//                    .build();
//        }
//
//        Map<String, List<InputPart>> formData = input.getFormDataMap();
//        List<InputPart> inputParts = formData.get("file");
//
//        if (inputParts == null || inputParts.isEmpty()) {
//            return Response.status(Response.Status.BAD_REQUEST)
//                    .entity("No file uploaded")
//                    .build();
//        }
//
//        try (InputStream inputStream = inputParts.get(0).getBody(InputStream.class, null);
//             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
//
//            CsvToBean<MovieCsv> csvToBean = new CsvToBeanBuilder<MovieCsv>(reader)
//                    .withType(MovieCsv.class)
//                    .withSeparator(';')
//                    .withIgnoreLeadingWhiteSpace(true)
//                    .build();
//
//            List<MovieCsv> movieCsvList = csvToBean.parse();
//
//            // Проверка на пустые или некорректные данные
//            if (movieCsvList.isEmpty()) {
//                return Response.status(Response.Status.BAD_REQUEST)
//                        .entity("No valid movie data found in the uploaded file.")
//                        .build();
//            }
//
//            System.out.println("Parsed Movies: " + movieCsvList);
//            service.importMovies(movieCsvList);
//
//            return Response.ok("Movies imported successfully, total: " + movieCsvList.size()).build();
//        } catch (IOException e) {
//            e.printStackTrace();
//            return Response.status(Response.Status.BAD_REQUEST)
//                    .entity("Error processing the CSV file: " + e.getMessage())
//                    .build();
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
//                    .entity("Internal server error: " + e.getMessage())
//                    .build();
//        }
//    }


    @GET
    @Path("/export")
    @Produces("text/csv")
    public Response export() {
        try {
            StringWriter writer = new StringWriter();
            StatefulBeanToCsv<MovieCsv> beanToCsv = new StatefulBeanToCsvBuilder<MovieCsv>(writer)
                    .withSeparator(';')
                    .build();
            beanToCsv.write(service.exportMovies());

            // Возвращаем CSV в ответе
            return Response.ok(writer.toString())
                    .header("Content-Disposition", "attachment; filename=\"movies.csv\"")
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError()
                    .entity("Произошла ошибка при экспорте CSV")
                    .build();
        }
    }

//    @GET
//    @Path("/export")
//    @Produces("application/octet-stream")
//    public Response export() {
//        try {
//            List<MovieCsv> movies = service.exportMovies();
//            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//            StatefulBeanToCsv<MovieCsv> beanToCsv = new StatefulBeanToCsvBuilder<MovieCsv>(new PrintWriter(byteArrayOutputStream))
//                    .withSeparator(';')
//                    .build();
//            beanToCsv.write(movies);
//
//            byte[] data = byteArrayOutputStream.toByteArray();
//
//            return Response.ok(data)
//                    .header("Content-Disposition", "attachment; filename=\"movies.csv\"")
//                    .build();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Response.serverError()
//                    .entity("Произошла ошибка при экспорте CSV")
//                    .build();
//        }
//    }

    @GET
    @Path("/{id}")
    public Response getMovie(@PathParam("id") Long id) {
        Movie movie = service.findById(id);
        if (movie != null) {
            return Response.ok(movie).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/count/all-by-user")
    public Long countAllByUser() {
        return service.countAllByUser();
    }

    @GET
    @Path("/count/all-public")
    public Long countAllPublic() {
        return service.countAllPublic();
    }

    @GET
    @Path("/all/{limit}/{page}")
    public List<Movie> getAll(
            @PathParam("limit")
            Integer limit,
            @PathParam("page")
            Integer page
    ) {
        return service.findAll(limit, page);
    }

    @GET
    @Path("/all-by-user/{limit}/{page}")
    public List<Movie> getAllByUser(
            @PathParam("limit")
            Integer limit,
            @PathParam("page")
            Integer page
    ) {
        return service.findAllByUser(limit, page);
    }

    @GET
    @Path("/all-public/{limit}/{page}")
    public List<Movie> getAllPublicMovies(
            @PathParam("limit")
            Integer limit,
            @PathParam("page")
            Integer page
    ) {
        return service.findAllPublic(limit, page);
    }

    @GET
    @Path("/all-by-user-filtered/{limit}/{page}")
    public List<Movie> getAllByUserFiltered(
            @PathParam("limit") Integer limit,
            @PathParam("page") Integer page,
            @QueryParam("filter") String filter,
            @QueryParam("columnWithRequest") String columnWithRequest
    ) {
        Map<String, String> columnMap = QueryParamParser.parseQueryParams(columnWithRequest);
        System.out.println("limit: " + limit);
        System.out.println("page: " + page);
        System.out.println("filter: " + filter);
        System.out.println("columnWithRequest: " + columnMap);
        return service.findAllByUserFiltered(limit, page, columnMap, filter);
    }

    @GET
    @Path("/all-public-filtered/{limit}/{page}")
    public List<Movie> getAllPublicFiltered(
            @PathParam("limit") Integer limit,
            @PathParam("page") Integer page,
            @QueryParam("filter") String filter,
            @QueryParam("columnWithRequest") String columnWithRequest
    ) {
        Map<String, String> columnMap = QueryParamParser.parseQueryParams(columnWithRequest);
        return service.findAllPublicFiltered(limit, page, columnMap, filter);
    }

    @PUT
    public Response updateMovie(@Valid Movie movie) {
        try {
            Movie updatedMovie = service.update(movie);
            if (updatedMovie != null) {
                return Response.ok(updatedMovie).build();
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("The data is not completely filled in. Please check the entered data.").build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteMovie(
            @PathParam("id")
            Long id
    ) {
        try {
            service.deleteById(id);
            return Response.status(Response.Status.NO_CONTENT).build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN).entity("This movie is not yours").build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}