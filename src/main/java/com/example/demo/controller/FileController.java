package com.example.demo.controller;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.entity.Note;
import com.example.demo.service.NoteService;

@RestController
@RequestMapping("/files")
@CrossOrigin(origins = "http://localhost:5173")
public class FileController {

    private final NoteService noteService;

    public FileController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
public String uploadFile(@RequestPart("file") MultipartFile file) {

        try {
            byte[] bytes = file.getBytes();

            PDDocument document = Loader.loadPDF(bytes);

            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);
            System.out.println("PDF FILE NAME: " + file.getOriginalFilename());
            System.out.println("EXTRACTED TEXT: " + text);
            System.out.println("EXTRACTED TEXT LENGTH: " + text.length());

            document.close();

            Note note = new Note(file.getOriginalFilename(), text);

            noteService.saveNote(note);

            return text;

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}